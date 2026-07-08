package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.interview.ReviewFlag;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfile;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfileRepository;
import com.interview.prep.platform.backend_core.review.dto.ReviewItemDto;
import com.interview.prep.platform.backend_core.review.dto.TodayQueueDto;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Today Queue (pipeline v2 §6): a deterministic, budget-filled priority
 * queue — open review flags first (interview > mock > rest), then the current
 * week's plan, then spaced repetition by decayed confidence. Recomputed on
 * every fetch so it self-heals when life happens; nothing is scheduled to days.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private static final int DEFAULT_EST_MINUTES = 45;
    private static final int STUDY_DAYS_PER_WEEK = 6;

    private final TopicRepository topicRepository;
    private final ReviewFlagRepository flagRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ResumeProfileRepository resumeProfileRepository;

    public TodayQueueDto getTodayQueue(Long userId) {
        int hoursPerWeek = userSettingsRepository.findByUserId(userId)
                .map(s -> s.getHoursPerWeek() != null ? s.getHoursPerWeek() : 10).orElse(10);
        int budgetMin = Math.max(30, hoursPerWeek * 60 / STUDY_DAYS_PER_WEEK);

        List<Topic> all = topicRepository.findByUserIdWithWeekOrdered(userId);
        if (all.isEmpty()) {
            return new TodayQueueDto(List.of(), budgetMin, 0, "no plan yet");
        }

        Map<String, ReviewItemDto> queue = new LinkedHashMap<>();
        int[] planned = {0};

        // 1. open review flags — interview-sourced first, then mock, then the rest
        List<ReviewFlag> flags = new ArrayList<>(flagRepository.findByUserIdAndResolved(userId, false));
        flags.sort(Comparator.comparingInt(f -> switch (f.getSource() != null ? f.getSource() : "") {
            case "interview" -> 0;
            case "mock" -> 1;
            default -> 2;
        }));
        for (ReviewFlag f : flags) {
            if (planned[0] >= budgetMin) break;
            topicRepository.findById(f.getTopicId()).ifPresent(t ->
                    add(queue, planned, new ReviewItemDto("flagged", t.getSlug(), t.getTitle(), f.getReason(), est(t))));
        }

        // 2. current week's topics, plan order
        Instant planStart = resumeProfileRepository.findByUserId(userId)
                .map(ResumeProfile::getUpdatedAt).orElse(Instant.now());
        List<Long> weekIds = all.stream().map(t -> t.getWeek().getId()).distinct().toList();
        long daysElapsed = Math.max(0, ChronoUnit.DAYS.between(planStart, Instant.now()));
        int weekIdx = (int) Math.min(daysElapsed / 7, weekIds.size() - 1L);
        Long currentWeekId = weekIds.get(weekIdx);
        for (Topic t : all) {
            if (planned[0] >= budgetMin) break;
            if (!t.getWeek().getId().equals(currentWeekId) || !"todo".equals(t.getStatus())) continue;
            add(queue, planned, new ReviewItemDto("new", t.getSlug(), t.getTitle(),
                    "week " + (weekIdx + 1) + " of your plan", est(t)));
        }

        // 3. spaced repetition — lowest effective (decayed) confidence first
        List<Topic> reviewed = all.stream()
                .filter(t -> "done".equals(t.getStatus()) && t.getConfidence() != null)
                .sorted(Comparator.comparingInt(this::effectiveConfidence))
                .toList();
        for (Topic t : reviewed) {
            if (planned[0] >= budgetMin) break;
            add(queue, planned, new ReviewItemDto("spaced", t.getSlug(), t.getTitle(),
                    "confidence slipping — " + effectiveConfidence(t) + "%", est(t)));
        }

        return new TodayQueueDto(List.copyOf(queue.values()), budgetMin, planned[0],
                velocity(all, planStart, weekIds.size()));
    }

    @Transactional
    public void markDone(Long userId, String slug) {
        Topic topic = topicRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        // Resolve flags
        List<ReviewFlag> flags = flagRepository.findByUserIdAndTopicIdAndResolved(userId, topic.getId(), false);
        flags.forEach(f -> f.setResolved(true));
        flagRepository.saveAll(flags);
        // Bump confidence and stamp
        int conf = topic.getConfidence() != null ? topic.getConfidence() : 0;
        topic.setConfidence(Math.min(100, conf + 20));
        topic.setLastReviewedAt(Instant.now());
        topicRepository.save(topic);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static void add(Map<String, ReviewItemDto> queue, int[] planned, ReviewItemDto item) {
        if (queue.putIfAbsent(item.topicSlug(), item) == null) {
            planned[0] += item.estMinutes();
        }
    }

    private int est(Topic t) {
        return t.getEstMinutes() != null ? t.getEstMinutes() : DEFAULT_EST_MINUTES;
    }

    /** confidence minus 1 point per day beyond a 7-day grace, mirroring the nightly decay job */
    private int effectiveConfidence(Topic t) {
        int conf = t.getConfidence() != null ? t.getConfidence() : 0;
        long days = t.getLastReviewedAt() == null ? 30
                : ChronoUnit.DAYS.between(t.getLastReviewedAt(), Instant.now());
        return (int) Math.max(0, conf - Math.max(0, days - 7));
    }

    private String velocity(List<Topic> all, Instant planStart, int totalWeeks) {
        long done = all.stream().filter(t -> "done".equals(t.getStatus())).count();
        double weeksElapsed = Math.min(totalWeeks,
                Math.max(0, ChronoUnit.DAYS.between(planStart, Instant.now())) / 7.0);
        double expected = weeksElapsed * ((double) all.size() / Math.max(1, totalWeeks));
        double perDay = (double) all.size() / Math.max(1, totalWeeks * 7L);
        long behindDays = Math.round((expected - done) / Math.max(perDay, 0.01));
        return behindDays <= 0 ? "on pace" : "~" + behindDays + " day" + (behindDays == 1 ? "" : "s") + " behind";
    }
}

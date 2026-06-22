package com.interview.prep.platform.backend_core.study;

import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.study.dto.OverallProgressDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgressService {

    private final PhaseRepository phaseRepository;
    private final TopicRepository topicRepository;
    private final ReviewFlagRepository reviewFlagRepository;
    private final ReadinessCalculator readinessCalculator;

    public OverallProgressDto getProgress(Long userId) {
        List<Topic> all = topicRepository.findByUserId(userId);
        int total = all.size();
        int done = (int) all.stream().filter(t -> "done".equals(t.getStatus())).count();
        int overallPct = total == 0 ? 0 : (int) (100.0 * done / total);

        int streak = computeStreak(all);
        long flaggedCount = reviewFlagRepository.countByUserIdAndResolved(userId, false);

        List<Phase> phases = phaseRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        List<OverallProgressDto.TrackReadinessDto> tracks = phases.stream().map(phase -> {
            List<Topic> phaseTopics = phase.getWeeks().stream()
                    .flatMap(w -> w.getTopics().stream()).toList();
            List<Long> topicIds = phaseTopics.stream().map(Topic::getId).toList();
            long openFlags = topicIds.isEmpty() ? 0
                    : reviewFlagRepository.countByUserIdAndTopicIdInAndResolved(userId, topicIds, false);
            int readiness = readinessCalculator.calculate(phaseTopics, openFlags);
            return new OverallProgressDto.TrackReadinessDto(phase.getName(), readiness);
        }).toList();

        return new OverallProgressDto(overallPct, streak, tracks, flaggedCount);
    }

    private int computeStreak(List<Topic> topics) {
        Set<LocalDate> reviewedDates = topics.stream()
                .filter(t -> t.getLastReviewedAt() != null)
                .map(t -> t.getLastReviewedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());
        if (reviewedDates.isEmpty()) return 0;
        int streak = 0;
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        while (reviewedDates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}

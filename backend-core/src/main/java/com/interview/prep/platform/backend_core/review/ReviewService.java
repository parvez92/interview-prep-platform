package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.interview.ReviewFlag;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.review.dto.ReviewItemDto;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.content.ExerciseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final TopicRepository topicRepository;
    private final ReviewFlagRepository flagRepository;
    private final ExerciseRepository exerciseRepository;

    public List<ReviewItemDto> getTodayReview(Long userId) {
        List<ReviewItemDto> items = new ArrayList<>();

        // (a) next unstarted topics in plan order
        topicRepository.findByUserIdOrdered(userId).stream()
                .filter(t -> "todo".equals(t.getStatus()))
                .limit(3)
                .forEach(t -> items.add(new ReviewItemDto("new", t.getSlug(), t.getTitle(), null)));

        // (b) open review flags (highest priority first)
        flagRepository.findByUserIdAndResolved(userId, false).stream()
                .limit(5)
                .forEach(f -> topicRepository.findById(f.getTopicId()).ifPresent(t ->
                        items.add(new ReviewItemDto("flagged", t.getSlug(), t.getTitle(), f.getReason()))));

        // (c) drill: first topic with exercises that isn't already in the list
        topicRepository.findByUserIdOrdered(userId).stream()
                .filter(t -> "done".equals(t.getStatus()))
                .filter(t -> exerciseRepository.existsByUserIdAndTopicId(userId, t.getId()))
                .filter(t -> items.stream().noneMatch(i -> i.topicSlug().equals(t.getSlug())))
                .findFirst()
                .ifPresent(t -> items.add(new ReviewItemDto("drill", t.getSlug(), t.getTitle(), null)));

        return items;
    }

    @Transactional
    public void markDone(Long userId, String slug) {
        Topic topic = topicRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new com.interview.prep.platform.backend_core.common.error.ApiException(
                        com.interview.prep.platform.backend_core.common.error.ErrorCode.NOT_FOUND,
                        org.springframework.http.HttpStatus.NOT_FOUND));
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
}

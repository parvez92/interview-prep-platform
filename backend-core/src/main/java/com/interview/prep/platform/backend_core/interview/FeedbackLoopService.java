package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedbackLoopService {

    private static final int LOW_RATING_THRESHOLD = 2;
    private static final int CONFIDENCE_DROP = 30;

    private final ReviewFlagRepository reviewFlagRepository;
    private final TopicRepository topicRepository;

    /** Called when a new interview question is logged or its rating changes. */
    @Transactional
    public void handleRating(InterviewQuestion question, String companyName) {
        if (question.getTopicId() == null) return;

        if (question.getSelfRating() <= LOW_RATING_THRESHOLD) {
            createFlag(question, companyName);
            lowerConfidence(question.getTopicId());
        } else {
            resolveFlags(question.getUserId(), question.getTopicId());
        }
    }

    /** Called when a mock interview session completes with a final score. */
    @Transactional
    public void handleMockScore(Long userId, Long topicId, int score) {
        if (topicId == null) return;
        if (score <= LOW_RATING_THRESHOLD) {
            ReviewFlag flag = new ReviewFlag();
            flag.setUserId(userId);
            flag.setTopicId(topicId);
            flag.setSource("mock");
            flag.setReason(String.format("scored %d/5 in mock interview", score));
            reviewFlagRepository.save(flag);
            lowerConfidence(topicId);
        } else {
            resolveFlags(userId, topicId);
        }
    }

    /** Called when a question is deleted — re-evaluate its flag. */
    @Transactional
    public void onQuestionRemoved(InterviewQuestion question) {
        if (question.getTopicId() == null) return;
        if (question.getSelfRating() <= LOW_RATING_THRESHOLD) {
            resolveFlags(question.getUserId(), question.getTopicId());
        }
    }

    private void createFlag(InterviewQuestion question, String companyName) {
        ReviewFlag flag = new ReviewFlag();
        flag.setUserId(question.getUserId());
        flag.setTopicId(question.getTopicId());
        flag.setSource("interview");
        flag.setReason(String.format("self-rated %d/5 at %s", question.getSelfRating(), companyName));
        reviewFlagRepository.save(flag);
    }

    private void lowerConfidence(Long topicId) {
        topicRepository.findById(topicId).ifPresent(topic -> {
            int current = topic.getConfidence() != null ? topic.getConfidence() : 80;
            topic.setConfidence(Math.max(0, current - CONFIDENCE_DROP));
            topicRepository.save(topic);
        });
    }

    private void resolveFlags(Long userId, Long topicId) {
        List<ReviewFlag> flags = reviewFlagRepository
                .findByUserIdAndTopicIdAndResolved(userId, topicId, false);
        flags.forEach(f -> f.setResolved(true));
        reviewFlagRepository.saveAll(flags);
    }
}

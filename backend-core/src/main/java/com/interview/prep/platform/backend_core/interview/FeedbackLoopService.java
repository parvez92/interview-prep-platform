package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackLoopService {

    private static final int LOW_RATING_THRESHOLD = 2;
    private static final int CONFIDENCE_DROP = 30;

    private final ReviewFlagRepository reviewFlagRepository;
    private final TopicRepository topicRepository;
    private final AiClient aiClient;

    /** Called when a new interview question is logged or its rating changes. */
    @Transactional
    public void handleRating(InterviewQuestion question, String companyName) {
        if (question.getTopicId() == null) return;

        if (question.getSelfRating() <= LOW_RATING_THRESHOLD) {
            createFlag(question, companyName);
            lowerConfidence(question.getTopicId());
            embedWeakAnswer(question, companyName);
        } else {
            resolveFlags(question.getUserId(), question.getTopicId());
            removeWeakAnswerEmbedding(question);
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
            removeWeakAnswerEmbedding(question);
        }
    }

    /**
     * Mirrors the weak question into the ai-service vector store so guide/question
     * generation can retrieve what was actually fumbled. Strictly best-effort:
     * logging an interview must never depend on the AI service being reachable.
     */
    private void embedWeakAnswer(InterviewQuestion question, String companyName) {
        try {
            aiClient.post(question.getUserId(), "/ai/embed-weak-answer", Map.of(
                    "question_id", question.getId(),
                    "topic_id", question.getTopicId(),
                    "text", String.format("Answered poorly (self-rated %d/5 at %s): %s",
                            question.getSelfRating(), companyName, question.getText())),
                    "embed-weak-answer");
        } catch (Exception e) {
            log.warn("weak-answer embedding skipped for question {}: {}", question.getId(), e.getMessage());
        }
    }

    private void removeWeakAnswerEmbedding(InterviewQuestion question) {
        try {
            aiClient.post(question.getUserId(), "/ai/delete-weak-answer",
                    Map.of("question_id", question.getId()), "delete-weak-answer");
        } catch (Exception e) {
            log.warn("weak-answer embedding cleanup skipped for question {}: {}", question.getId(), e.getMessage());
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

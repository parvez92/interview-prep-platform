package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackLoopServiceTest {

    @Mock ReviewFlagRepository flagRepository;
    @Mock TopicRepository topicRepository;
    @Mock AiClient aiClient;
    @InjectMocks FeedbackLoopService service;

    private InterviewQuestion question(int rating, Long topicId) {
        InterviewQuestion q = new InterviewQuestion();
        q.setUserId(1L); q.setInterviewId(10L);
        q.setTopicId(topicId); q.setText("Q"); q.setSelfRating(rating);
        return q;
    }

    @Test
    void lowRating_createsFlag() {
        InterviewQuestion q = question(1, 5L);
        Topic topic = new Topic(); topic.setConfidence(80);
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicRepository.save(any())).thenReturn(topic);

        service.handleRating(q, "Acme");

        ArgumentCaptor<ReviewFlag> captor = ArgumentCaptor.forClass(ReviewFlag.class);
        verify(flagRepository).save(captor.capture());
        ReviewFlag flag = captor.getValue();
        assertThat(flag.getTopicId()).isEqualTo(5L);
        assertThat(flag.getSource()).isEqualTo("interview");
        assertThat(flag.isResolved()).isFalse();
    }

    @Test
    void lowRating_lowersConfidence() {
        InterviewQuestion q = question(2, 5L);
        Topic topic = new Topic(); topic.setConfidence(80);
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicRepository.save(any())).thenReturn(topic);

        service.handleRating(q, "Acme");

        verify(topicRepository).save(argThat(t -> t.getConfidence() == 50));
    }

    @Test
    void confidenceFloorIsZero() {
        InterviewQuestion q = question(1, 5L);
        Topic topic = new Topic(); topic.setConfidence(10);
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicRepository.save(any())).thenReturn(topic);

        service.handleRating(q, "Corp");

        verify(topicRepository).save(argThat(t -> t.getConfidence() >= 0));
    }

    @Test
    void highRating_resolvesFlags() {
        InterviewQuestion q = question(4, 5L);
        ReviewFlag flag = new ReviewFlag(); flag.setResolved(false);
        when(flagRepository.findByUserIdAndTopicIdAndResolved(1L, 5L, false)).thenReturn(List.of(flag));
        when(flagRepository.saveAll(any())).thenReturn(List.of());

        service.handleRating(q, "Corp");

        verify(flagRepository, never()).save(any());
        assertThat(flag.isResolved()).isTrue();
    }

    @Test
    void noTopicId_doesNothing() {
        InterviewQuestion q = question(1, null);
        service.handleRating(q, "Acme");
        verifyNoInteractions(flagRepository, topicRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lowRating_embedsWeakAnswer() {
        InterviewQuestion q = question(1, 5L);
        q.setId(42L);
        Topic topic = new Topic(); topic.setConfidence(80);
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicRepository.save(any())).thenReturn(topic);

        service.handleRating(q, "Acme");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(aiClient).post(eq(1L), eq("/ai/embed-weak-answer"), captor.capture(), eq("embed-weak-answer"));
        assertThat(captor.getValue().get("question_id")).isEqualTo(42L);
        assertThat((String) captor.getValue().get("text")).contains("Q").contains("Acme");
    }

    @Test
    void highRating_removesWeakAnswerEmbedding() {
        InterviewQuestion q = question(4, 5L);
        q.setId(42L);
        when(flagRepository.findByUserIdAndTopicIdAndResolved(1L, 5L, false)).thenReturn(List.of());

        service.handleRating(q, "Acme");

        verify(aiClient).post(eq(1L), eq("/ai/delete-weak-answer"), eq(Map.of("question_id", 42L)), eq("delete-weak-answer"));
        verify(aiClient, never()).post(anyLong(), eq("/ai/embed-weak-answer"), any(), anyString());
    }

    @Test
    void aiServiceDown_doesNotBreakFlagCreation() {
        InterviewQuestion q = question(1, 5L);
        q.setId(42L);
        Topic topic = new Topic(); topic.setConfidence(80);
        when(topicRepository.findById(5L)).thenReturn(Optional.of(topic));
        when(flagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicRepository.save(any())).thenReturn(topic);
        when(aiClient.post(anyLong(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("ai-service unreachable"));

        assertThatCode(() -> service.handleRating(q, "Acme")).doesNotThrowAnyException();
        verify(flagRepository).save(any());
    }

    @Test
    void onQuestionRemoved_lowRating_resolvesFlags() {
        InterviewQuestion q = question(2, 5L);
        ReviewFlag flag = new ReviewFlag(); flag.setResolved(false);
        when(flagRepository.findByUserIdAndTopicIdAndResolved(1L, 5L, false)).thenReturn(List.of(flag));
        when(flagRepository.saveAll(any())).thenReturn(List.of());

        service.onQuestionRemoved(q);

        assertThat(flag.isResolved()).isTrue();
    }
}

package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackLoopServiceTest {

    @Mock ReviewFlagRepository flagRepository;
    @Mock TopicRepository topicRepository;
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
    void onQuestionRemoved_lowRating_resolvesFlags() {
        InterviewQuestion q = question(2, 5L);
        ReviewFlag flag = new ReviewFlag(); flag.setResolved(false);
        when(flagRepository.findByUserIdAndTopicIdAndResolved(1L, 5L, false)).thenReturn(List.of(flag));
        when(flagRepository.saveAll(any())).thenReturn(List.of());

        service.onQuestionRemoved(q);

        assertThat(flag.isResolved()).isTrue();
    }
}

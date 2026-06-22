package com.interview.prep.platform.backend_core.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findByInterviewId(Long interviewId);
    Optional<InterviewQuestion> findByIdAndUserId(Long id, Long userId);
}

package com.interview.prep.platform.backend_core.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByUserIdAndTopicIdOrderByDisplayOrderAsc(Long userId, Long topicId);
    Optional<Question> findByIdAndUserId(Long id, Long userId);
}

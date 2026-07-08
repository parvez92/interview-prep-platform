package com.interview.prep.platform.backend_core.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByUserIdAndTopicIdOrderByDisplayOrderAsc(Long userId, Long topicId);
    List<Exercise> findByUserIdAndTopicIdAndSource(Long userId, Long topicId, String source);
    Optional<Exercise> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndTopicId(Long userId, Long topicId);
}

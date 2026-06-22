package com.interview.prep.platform.backend_core.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByUserIdAndTopicIdOrderByDisplayOrderAsc(Long userId, Long topicId);
    Optional<Resource> findByIdAndUserId(Long id, Long userId);
}

package com.interview.prep.platform.backend_core.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewFlagRepository extends JpaRepository<ReviewFlag, Long> {
    List<ReviewFlag> findByUserIdAndTopicIdAndResolved(Long userId, Long topicId, boolean resolved);
    List<ReviewFlag> findByUserIdAndResolved(Long userId, boolean resolved);
    long countByUserIdAndResolved(Long userId, boolean resolved);
    long countByUserIdAndTopicIdInAndResolved(Long userId, Collection<Long> topicIds, boolean resolved);
}

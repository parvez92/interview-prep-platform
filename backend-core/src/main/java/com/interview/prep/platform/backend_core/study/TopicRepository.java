package com.interview.prep.platform.backend_core.study;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findByUserIdAndSlug(Long userId, String slug);

    List<Topic> findByUserIdAndStatus(Long userId, String status);

    List<Topic> findByUserId(Long userId);

    boolean existsByUserIdAndSlug(Long userId, String slug);

    @Query("SELECT t FROM Topic t WHERE t.userId = :userId ORDER BY t.displayOrder ASC")
    List<Topic> findByUserIdOrdered(Long userId);

    @Query("""
            SELECT t FROM Topic t JOIN FETCH t.week w JOIN FETCH w.phase p
            WHERE t.userId = :userId
            ORDER BY p.displayOrder ASC, w.displayOrder ASC, t.displayOrder ASC""")
    List<Topic> findByUserIdWithWeekOrdered(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);
}

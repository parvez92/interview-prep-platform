package com.interview.prep.platform.backend_core.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Interview> findByIdAndUserId(Long id, Long userId);
}

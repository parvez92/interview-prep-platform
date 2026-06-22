package com.interview.prep.platform.backend_core.mock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockSessionRepository extends JpaRepository<MockSession, Long> {
    Optional<MockSession> findByIdAndUserId(Long id, Long userId);
}

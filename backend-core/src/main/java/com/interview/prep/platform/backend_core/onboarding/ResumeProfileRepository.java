package com.interview.prep.platform.backend_core.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, Long> {
    Optional<ResumeProfile> findByUserId(Long userId);
}

package com.interview.prep.platform.backend_core.preppack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrepPackRepository extends JpaRepository<PrepPack, Long> {
    Optional<PrepPack> findByInterviewIdAndUserId(Long interviewId, Long userId);
}

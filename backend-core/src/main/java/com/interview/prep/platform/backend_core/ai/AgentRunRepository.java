package com.interview.prep.platform.backend_core.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
    List<AgentRun> findByUserIdOrderByStartedAtDesc(Long userId);
}

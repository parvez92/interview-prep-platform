package com.interview.prep.platform.backend_core.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {
    List<UsageLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(u.costUsd), 0) FROM UsageLog u WHERE u.userId = :userId AND u.createdAt >= :from")
    BigDecimal sumCostSince(Long userId, Instant from);
}

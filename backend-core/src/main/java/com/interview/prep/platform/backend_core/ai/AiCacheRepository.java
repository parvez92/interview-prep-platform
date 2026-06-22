package com.interview.prep.platform.backend_core.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCacheRepository extends JpaRepository<AiCache, Long> {
    Optional<AiCache> findByCacheKey(String cacheKey);
}

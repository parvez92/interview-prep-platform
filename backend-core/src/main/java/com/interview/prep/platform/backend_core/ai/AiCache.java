package com.interview.prep.platform.backend_core.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ai_cache")
@Getter @Setter @NoArgsConstructor
public class AiCache {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String cacheKey;

    @Column(nullable = false, columnDefinition = "text")
    private String responseJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

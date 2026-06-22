package com.interview.prep.platform.backend_core.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "usage_log")
@Getter @Setter @NoArgsConstructor
public class UsageLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int promptTokens;

    @Column(nullable = false)
    private int completionTokens;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

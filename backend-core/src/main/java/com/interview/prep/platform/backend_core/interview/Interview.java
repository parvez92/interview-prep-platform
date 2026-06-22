package com.interview.prep.platform.backend_core.interview;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "interview")
@Getter @Setter @NoArgsConstructor
public class Interview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String stage = "applied";

    private String round;
    private LocalDate scheduledAt;

    @Column(nullable = false)
    private String outcome = "pending";

    @Column(columnDefinition = "text")
    private String jdText;

    private Long jobAlertId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

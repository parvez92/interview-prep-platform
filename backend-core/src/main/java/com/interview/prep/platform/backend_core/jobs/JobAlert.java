package com.interview.prep.platform.backend_core.jobs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "job_alert")
@Getter @Setter @NoArgsConstructor
public class JobAlert {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(unique = true)
    private String emailMsgId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status = "new";

    private Double matchScore;

    @Column(columnDefinition = "text")
    private String jdText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metaJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

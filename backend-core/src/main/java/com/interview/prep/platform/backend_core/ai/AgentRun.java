package com.interview.prep.platform.backend_core.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "agent_run")
@Getter @Setter @NoArgsConstructor
public class AgentRun {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String status = "running";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String inputJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String outputJson;

    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    private Instant finishedAt;

    private int steps;
}

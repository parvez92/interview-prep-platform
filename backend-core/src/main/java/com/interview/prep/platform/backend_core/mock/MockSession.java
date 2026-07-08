package com.interview.prep.platform.backend_core.mock;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "mock_session")
@Getter @Setter @NoArgsConstructor
public class MockSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** technical | system-design | behavioral */
    @Column(nullable = false)
    private String type = "technical";

    /** set only when the session targets a real study-plan topic */
    private String topicSlug;

    @Column(nullable = false)
    private String status = "in_progress";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String turnsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String feedbackJson;

    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    private Instant finishedAt;
}

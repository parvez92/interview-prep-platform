package com.interview.prep.platform.backend_core.study;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "topic")
@Getter @Setter @NoArgsConstructor
public class Topic {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id", nullable = false)
    private Week week;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String tag = "new";

    @Column(nullable = false)
    private String source = "standard";

    @Column(columnDefinition = "text")
    private String concept;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String points = "[]";

    @Column(columnDefinition = "text")
    private String angle;

    @Column(nullable = false)
    private String status = "todo";

    @Column(nullable = false)
    private boolean isCustom = false;

    private Integer confidence;
    private Instant lastReviewedAt;

    @Column(nullable = false)
    private int displayOrder;
}

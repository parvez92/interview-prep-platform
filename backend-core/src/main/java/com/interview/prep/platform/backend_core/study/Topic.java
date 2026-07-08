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

    /** Plan-assigned category (dsa, system_design, …) — not the UI badge tag */
    private String category;

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

    // ── pipeline v2 ─────────────────────────────────────────────────────────────
    /** estimated study effort, minutes — drives the today queue's budget fill */
    private Integer estMinutes;

    /** depth generation failed validation twice — UI offers manual regen */
    @Column(nullable = false)
    private boolean needsReview = false;

    /** slug of the coarse plan topic this one was split from by the depth pass */
    private String coarseParent;

    /** none|maybe|likely — whether the depth pass should consider splitting this coarse topic */
    private String splitHint;

    @Column(nullable = false)
    private int displayOrder;
}

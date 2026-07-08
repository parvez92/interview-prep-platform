package com.interview.prep.platform.backend_core.study;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "week")
@Getter @Setter @NoArgsConstructor
public class Week {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    // ── narrative pass (pipeline v2): story over the frozen structure ──────────
    /** [{"week": 1, "why": "..."}] — earlier weeks this one builds on */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String buildsOn;

    /** what completing this week unlocks later in the plan */
    @Column(columnDefinition = "text")
    private String unlocks;

    /** one sentence rendered between week sections in the UI */
    @Column(columnDefinition = "text")
    private String bridge;

    /** optional résumé tie-in ("your TIBCO background maps 1:1 to these EIP patterns") */
    @Column(columnDefinition = "text")
    private String anchor;

    @Column(nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "week", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<Topic> topics = new ArrayList<>();
}

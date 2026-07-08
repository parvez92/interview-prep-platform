package com.interview.prep.platform.backend_core.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "exercise")
@Getter @Setter @NoArgsConstructor
public class Exercise {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String repoUrl;

    @Column(nullable = false)
    private boolean done = false;

    /** "manual" (user-entered) or "ai" — AI rows are replaced on regeneration */
    @Column(nullable = false)
    private String source = "manual";

    @Column(nullable = false)
    private int displayOrder;
}

package com.interview.prep.platform.backend_core.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "resource")
@Getter @Setter @NoArgsConstructor
public class Resource {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 2000)
    private String url;

    /** "manual" (user-entered) or "ai" — AI rows are replaced on regeneration */
    @Column(nullable = false)
    private String source = "manual";

    @Column(nullable = false)
    private int displayOrder;
}

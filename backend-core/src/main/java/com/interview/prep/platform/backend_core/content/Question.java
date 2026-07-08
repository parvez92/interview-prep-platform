package com.interview.prep.platform.backend_core.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "question")
@Getter @Setter @NoArgsConstructor
public class Question {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long topicId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    /** "manual" (user-entered) or "ai" — AI rows are replaced on regeneration */
    @Column(nullable = false)
    private String source = "manual";

    /** conceptual|tradeoff|implementation|debugging|scenario (pipeline v2) */
    @Column(length = 16)
    private String type;

    @Column(nullable = false)
    private int displayOrder;
}

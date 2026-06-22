package com.interview.prep.platform.backend_core.content;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "note")
@Getter @Setter @NoArgsConstructor
public class Note {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long topicId;

    @Column(nullable = false, columnDefinition = "text")
    private String contentMd = "";

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}

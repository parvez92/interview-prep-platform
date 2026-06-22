package com.interview.prep.platform.backend_core.interview;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "interview_question")
@Getter @Setter @NoArgsConstructor
public class InterviewQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long interviewId;

    private Long topicId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private int selfRating;
}

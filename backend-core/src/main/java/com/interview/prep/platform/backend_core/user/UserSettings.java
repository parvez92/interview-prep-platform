package com.interview.prep.platform.backend_core.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String llmProvider = "anthropic";

    private String llmModelStrong;
    private String llmModelCheap;
    private String ollamaUrl;

    @Column(nullable = false)
    private BigDecimal monthlyBudgetUsd = new BigDecimal("20.00");

    @Column(nullable = false)
    private boolean onboarded = false;

    private String targetRole;
    private String targetLevel;
    private Integer prepWeeks;
    private Integer hoursPerWeek;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String interests = "[]";
}

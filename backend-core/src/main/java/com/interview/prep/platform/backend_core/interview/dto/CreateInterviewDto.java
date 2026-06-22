package com.interview.prep.platform.backend_core.interview.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateInterviewDto(
        @NotBlank String company,
        @NotBlank String role,
        @NotBlank String stage,
        String round,
        LocalDate scheduledAt,
        String jdText
) {}

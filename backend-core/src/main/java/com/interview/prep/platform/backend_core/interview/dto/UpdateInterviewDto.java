package com.interview.prep.platform.backend_core.interview.dto;

import java.time.LocalDate;

public record UpdateInterviewDto(
        String stage,
        String outcome,
        String round,
        LocalDate scheduledAt,
        String jdText,
        String notes
) {}

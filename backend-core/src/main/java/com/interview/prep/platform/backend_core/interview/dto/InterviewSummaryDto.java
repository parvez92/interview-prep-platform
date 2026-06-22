package com.interview.prep.platform.backend_core.interview.dto;

import java.time.LocalDate;
import java.util.List;

public record InterviewSummaryDto(
        Long id,
        String company,
        String role,
        String stage,
        String round,
        LocalDate scheduledAt,
        String outcome,
        List<String> weak
) {}

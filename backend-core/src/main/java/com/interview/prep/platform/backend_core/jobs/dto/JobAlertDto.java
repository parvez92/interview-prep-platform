package com.interview.prep.platform.backend_core.jobs.dto;

import java.time.Instant;

public record JobAlertDto(
        Long id,
        String company,
        String role,
        String status,
        Double matchScore,
        String jdText,
        Instant createdAt
) {}

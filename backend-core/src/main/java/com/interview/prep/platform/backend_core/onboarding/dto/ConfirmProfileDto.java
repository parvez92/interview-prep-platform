package com.interview.prep.platform.backend_core.onboarding.dto;

import java.util.Map;

public record ConfirmProfileDto(
        Map<String, Object> profile,
        String targetRole,
        String targetLevel,
        Integer prepWeeks
) {}

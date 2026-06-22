package com.interview.prep.platform.backend_core.study.dto;

import java.util.List;

public record PhaseSummaryDto(
        String code,
        String name,
        String icon,
        String blurb,
        ProgressDto progress,
        List<WeekSummaryDto> weeks
) {}

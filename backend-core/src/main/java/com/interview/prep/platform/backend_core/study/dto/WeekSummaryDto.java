package com.interview.prep.platform.backend_core.study.dto;

import java.util.List;

public record WeekSummaryDto(
        String code,
        String title,
        List<TopicSummaryDto> topics
) {}

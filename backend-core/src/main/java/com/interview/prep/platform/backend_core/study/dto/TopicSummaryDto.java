package com.interview.prep.platform.backend_core.study.dto;

public record TopicSummaryDto(
        String slug,
        String code,
        String title,
        String tag,
        String source,
        String priority,
        String status,
        Integer confidence
) {}

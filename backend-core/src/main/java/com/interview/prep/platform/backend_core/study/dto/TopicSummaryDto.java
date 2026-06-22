package com.interview.prep.platform.backend_core.study.dto;

public record TopicSummaryDto(
        String slug,
        String code,
        String title,
        String tag,
        String source,
        String status,
        Integer confidence
) {}

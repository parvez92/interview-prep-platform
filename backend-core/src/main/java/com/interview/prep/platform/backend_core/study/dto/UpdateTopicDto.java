package com.interview.prep.platform.backend_core.study.dto;

import java.util.List;

public record UpdateTopicDto(
        String title,
        String tag,
        String status,
        String concept,
        List<String> points,
        String angle,
        Integer confidence,
        String priority
) {}

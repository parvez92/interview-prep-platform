package com.interview.prep.platform.backend_core.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateTopicDto(
        @NotBlank String weekCode,
        @NotBlank String title,
        @NotBlank @Pattern(regexp = "new|refresh|dsa|exp") String tag,
        String concept,
        List<String> points,
        String angle
) {}

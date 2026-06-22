package com.interview.prep.platform.backend_core.star.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record StarStoryDto(
        Long id,
        @NotBlank String title,
        String situation,
        String task,
        String action,
        String result,
        List<String> tags
) {}

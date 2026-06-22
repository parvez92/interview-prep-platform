package com.interview.prep.platform.backend_core.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LogQuestionDto(
        @NotBlank String text,
        String topicSlug,
        @Min(1) @Max(5) int selfRating
) {}

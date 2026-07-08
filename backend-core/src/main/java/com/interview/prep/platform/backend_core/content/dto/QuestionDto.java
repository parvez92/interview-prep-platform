package com.interview.prep.platform.backend_core.content.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionDto(Long id, @NotBlank String text, int displayOrder, String type) {}

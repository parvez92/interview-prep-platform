package com.interview.prep.platform.backend_core.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ExerciseDto(Long id, @NotBlank String title, String repoUrl, boolean done, int displayOrder,
                          Integer estMinutes) {}

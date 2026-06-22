package com.interview.prep.platform.backend_core.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ResourceDto(Long id, @NotBlank String label, @NotBlank String url, int displayOrder) {}

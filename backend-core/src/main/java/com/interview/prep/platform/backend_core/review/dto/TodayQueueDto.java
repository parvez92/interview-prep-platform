package com.interview.prep.platform.backend_core.review.dto;

import java.util.List;

/** The computed today queue (pipeline v2 §6) — recomputed on every fetch, never stored. */
public record TodayQueueDto(List<ReviewItemDto> items, int budgetMin, int plannedMin, String velocity) {}

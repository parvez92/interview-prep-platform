package com.interview.prep.platform.backend_core.review.dto;

public record ReviewItemDto(String kind, String topicSlug, String label, String note, Integer estMinutes) {}

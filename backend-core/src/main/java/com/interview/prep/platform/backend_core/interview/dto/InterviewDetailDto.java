package com.interview.prep.platform.backend_core.interview.dto;

import java.time.LocalDate;
import java.util.List;

public record InterviewDetailDto(
        Long id,
        String company,
        String role,
        String stage,
        String round,
        LocalDate scheduledAt,
        String outcome,
        String jdText,
        String notes,
        List<QuestionDto> questions,
        List<String> weak
) {
    public record QuestionDto(Long id, String text, String topicSlug, int selfRating) {}
}

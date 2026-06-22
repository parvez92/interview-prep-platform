package com.interview.prep.platform.backend_core.study.dto;

import com.interview.prep.platform.backend_core.content.dto.AttachmentDto;
import com.interview.prep.platform.backend_core.content.dto.ExerciseDto;
import com.interview.prep.platform.backend_core.content.dto.QuestionDto;
import com.interview.prep.platform.backend_core.content.dto.ResourceDto;

import java.time.Instant;
import java.util.List;

public record TopicDetailDto(
        Long id,
        String slug,
        String code,
        String title,
        String tag,
        String source,
        String status,
        boolean isCustom,
        Integer confidence,
        Instant lastReviewedAt,
        DeepDiveDto deepDive,
        NoteDto note,
        List<ResourceDto> resources,
        List<ExerciseDto> exercises,
        List<QuestionDto> questions
) {
    public record DeepDiveDto(String concept, List<String> points, String angle) {}
    public record NoteDto(String contentMd, Instant updatedAt, List<AttachmentDto> attachments) {}
}

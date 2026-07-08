package com.interview.prep.platform.backend_core.study;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.content.*;
import com.interview.prep.platform.backend_core.content.dto.*;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.study.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyService {

    private final PhaseRepository phaseRepository;
    private final WeekRepository weekRepository;
    private final TopicRepository topicRepository;
    private final NoteRepository noteRepository;
    private final AttachmentRepository attachmentRepository;
    private final ResourceRepository resourceRepository;
    private final ExerciseRepository exerciseRepository;
    private final QuestionRepository questionRepository;
    private final ReviewFlagRepository reviewFlagRepository;
    private final ObjectMapper objectMapper;

    public List<PhaseSummaryDto> getPhases(Long userId) {
        return phaseRepository.findByUserIdOrderByDisplayOrderAsc(userId).stream()
                .map(phase -> {
                    List<Topic> allTopics = phase.getWeeks().stream()
                            .flatMap(w -> w.getTopics().stream()).toList();
                    int done = (int) allTopics.stream().filter(t -> "done".equals(t.getStatus())).count();
                    int total = allTopics.size();
                    List<WeekSummaryDto> weeks = phase.getWeeks().stream()
                            .map(w -> new WeekSummaryDto(w.getCode(), w.getTitle(), w.getBridge(), w.getAnchor(),
                                    w.getTopics().stream().map(this::toTopicSummary).toList()))
                            .toList();
                    return new PhaseSummaryDto(phase.getCode(), phase.getName(), phase.getIcon(),
                            phase.getBlurb(), new ProgressDto(done, total), weeks);
                }).toList();
    }

    public TopicDetailDto getTopicDetail(Long userId, String slug) {
        Topic topic = requireOwned(userId, slug);
        return buildDetail(userId, topic);
    }

    @Transactional
    public TopicDetailDto createTopic(Long userId, CreateTopicDto dto) {
        Week week = weekRepository.findByUserIdAndCode(userId, dto.weekCode())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Week not found: " + dto.weekCode()));
        String slug = uniqueSlug(userId, dto.title());
        int order = topicRepository.countByUserId(userId) > 0
                ? topicRepository.findByUserIdOrdered(userId).stream()
                        .mapToInt(Topic::getDisplayOrder).max().orElse(0) + 1
                : 0;
        Topic topic = new Topic();
        topic.setUserId(userId);
        topic.setWeek(week);
        topic.setCode(slug.toUpperCase().replace("-", "_"));
        topic.setSlug(slug);
        topic.setTitle(dto.title());
        topic.setTag(dto.tag());
        topic.setSource("custom");
        topic.setStatus("todo");
        topic.setCustom(true);
        topic.setConcept(dto.concept());
        topic.setPoints(toJson(dto.points() != null ? dto.points() : List.of()));
        topic.setAngle(dto.angle());
        topic.setDisplayOrder(order);
        topicRepository.save(topic);
        return buildDetail(userId, topic);
    }

    @Transactional
    public TopicDetailDto updateTopic(Long userId, String slug, UpdateTopicDto dto) {
        Topic topic = requireOwned(userId, slug);
        if (dto.title() != null) topic.setTitle(dto.title());
        if (dto.tag() != null) topic.setTag(dto.tag());
        if (dto.concept() != null) topic.setConcept(dto.concept());
        if (dto.points() != null) topic.setPoints(toJson(dto.points()));
        if (dto.angle() != null) topic.setAngle(dto.angle());
        if (dto.confidence() != null) topic.setConfidence(dto.confidence());
        if (dto.status() != null) {
            topic.setStatus(dto.status());
            if ("done".equals(dto.status())) {
                topic.setLastReviewedAt(Instant.now());
                if (topic.getConfidence() == null) topic.setConfidence(80);
            }
        }
        topicRepository.save(topic);
        return buildDetail(userId, topic);
    }

    @Transactional
    public void deleteTopic(Long userId, String slug) {
        Topic topic = requireOwned(userId, slug);
        if (!topic.isCustom()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Seeded topics cannot be deleted — hide or skip instead");
        }
        topicRepository.delete(topic);
    }

    public Topic requireOwned(Long userId, String slug) {
        return topicRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Topic not found: " + slug));
    }

    private TopicSummaryDto toTopicSummary(Topic t) {
        return new TopicSummaryDto(t.getSlug(), t.getCode(), t.getTitle(),
                t.getTag(), t.getSource(), t.getStatus(), t.getConfidence());
    }

    private TopicDetailDto buildDetail(Long userId, Topic topic) {
        Note note = noteRepository.findByUserIdAndTopicId(userId, topic.getId()).orElse(null);
        List<AttachmentDto> attachments = note == null ? List.of()
                : attachmentRepository.findByNoteId(note.getId()).stream()
                        .map(a -> new AttachmentDto(a.getId(), "/api/attachments/" + a.getId(),
                                a.getOriginalName(), a.getMimeType()))
                        .toList();
        TopicDetailDto.NoteDto noteDto = new TopicDetailDto.NoteDto(
                note != null ? note.getContentMd() : "",
                note != null ? note.getUpdatedAt() : null,
                attachments);
        List<ResourceDto> resources = resourceRepository
                .findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(r -> new ResourceDto(r.getId(), r.getLabel(), r.getUrl(), r.getDisplayOrder()))
                .toList();
        List<ExerciseDto> exercises = exerciseRepository
                .findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(e -> new ExerciseDto(e.getId(), e.getTitle(), e.getRepoUrl(), e.isDone(), e.getDisplayOrder(), e.getEstMinutes()))
                .toList();
        List<QuestionDto> questions = questionRepository
                .findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(q -> new QuestionDto(q.getId(), q.getText(), q.getDisplayOrder(), q.getType()))
                .toList();
        TopicDetailDto.DeepDiveDto deepDive = new TopicDetailDto.DeepDiveDto(
                topic.getConcept(), parseList(topic.getPoints()), topic.getAngle());
        return new TopicDetailDto(topic.getId(), topic.getSlug(), topic.getCode(), topic.getTitle(),
                topic.getTag(), topic.getSource(), topic.getStatus(), topic.isCustom(),
                topic.getConfidence(), topic.getLastReviewedAt(), deepDive, noteDto, resources, exercises, questions);
    }

    private String uniqueSlug(Long userId, String title) {
        String base = title.toLowerCase().replaceAll("[^a-z0-9\\s-]", "").trim().replaceAll("\\s+", "-");
        if (base.isEmpty()) base = "topic";
        String slug = base;
        while (topicRepository.existsByUserIdAndSlug(userId, slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private List<String> parseList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<?> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}

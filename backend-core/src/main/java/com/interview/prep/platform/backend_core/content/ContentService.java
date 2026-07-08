package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.content.dto.ExerciseDto;
import com.interview.prep.platform.backend_core.content.dto.QuestionDto;
import com.interview.prep.platform.backend_core.content.dto.ResourceDto;
import com.interview.prep.platform.backend_core.study.StudyService;
import com.interview.prep.platform.backend_core.study.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private static final String SOURCE_AI = "ai";

    private final ResourceRepository resourceRepository;
    private final ExerciseRepository exerciseRepository;
    private final QuestionRepository questionRepository;
    private final StudyService studyService;

    // ── AI-generated content: replace previous AI rows, keep manual ones ────────

    @Transactional
    public void replaceAiResources(Long userId, String slug, List<ResourceDto> items) {
        Topic topic = studyService.requireOwned(userId, slug);
        resourceRepository.deleteAll(resourceRepository.findByUserIdAndTopicIdAndSource(userId, topic.getId(), SOURCE_AI));
        List<Resource> kept = resourceRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId());
        Set<String> seenUrls = kept.stream().map(r -> r.getUrl().strip()).collect(Collectors.toCollection(HashSet::new));
        int order = kept.size();
        for (ResourceDto dto : items) {
            if (dto.url() == null || !seenUrls.add(dto.url().strip())) continue;
            Resource r = new Resource();
            r.setUserId(userId); r.setTopicId(topic.getId());
            r.setLabel(dto.label()); r.setUrl(dto.url());
            r.setSource(SOURCE_AI); r.setDisplayOrder(order++);
            resourceRepository.save(r);
        }
    }

    @Transactional
    public void replaceAiExercises(Long userId, String slug, List<ExerciseDto> items) {
        Topic topic = studyService.requireOwned(userId, slug);
        List<Exercise> oldAi = exerciseRepository.findByUserIdAndTopicIdAndSource(userId, topic.getId(), SOURCE_AI);
        // carry done-state over to regenerated exercises with the same title
        Map<String, Boolean> doneByTitle = new HashMap<>();
        for (Exercise e : oldAi) doneByTitle.put(normalize(e.getTitle()), e.isDone());
        exerciseRepository.deleteAll(oldAi);
        List<Exercise> kept = exerciseRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId());
        Set<String> seenTitles = kept.stream().map(e -> normalize(e.getTitle())).collect(Collectors.toCollection(HashSet::new));
        int order = kept.size();
        for (ExerciseDto dto : items) {
            String key = normalize(dto.title());
            if (!seenTitles.add(key)) continue;
            Exercise e = new Exercise();
            e.setUserId(userId); e.setTopicId(topic.getId());
            e.setTitle(dto.title()); e.setRepoUrl(dto.repoUrl());
            e.setDone(doneByTitle.getOrDefault(key, false));
            e.setSource(SOURCE_AI); e.setDisplayOrder(order++);
            exerciseRepository.save(e);
        }
    }

    @Transactional
    public void replaceAiQuestions(Long userId, String slug, List<QuestionDto> items) {
        Topic topic = studyService.requireOwned(userId, slug);
        questionRepository.deleteAll(questionRepository.findByUserIdAndTopicIdAndSource(userId, topic.getId(), SOURCE_AI));
        List<Question> kept = questionRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId());
        Set<String> seenTexts = kept.stream().map(q -> normalize(q.getText())).collect(Collectors.toCollection(HashSet::new));
        int order = kept.size();
        for (QuestionDto dto : items) {
            if (!seenTexts.add(normalize(dto.text()))) continue;
            Question q = new Question();
            q.setUserId(userId); q.setTopicId(topic.getId()); q.setText(dto.text());
            q.setSource(SOURCE_AI); q.setDisplayOrder(order++);
            questionRepository.save(q);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.strip().toLowerCase();
    }

    // ── Resources ──────────────────────────────────────────────────────────────

    public List<ResourceDto> listResources(Long userId, String slug) {
        Topic topic = studyService.requireOwned(userId, slug);
        return resourceRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(r -> new ResourceDto(r.getId(), r.getLabel(), r.getUrl(), r.getDisplayOrder())).toList();
    }

    @Transactional
    public ResourceDto createResource(Long userId, String slug, ResourceDto dto) {
        Topic topic = studyService.requireOwned(userId, slug);
        int order = resourceRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).size();
        Resource r = new Resource();
        r.setUserId(userId); r.setTopicId(topic.getId());
        r.setLabel(dto.label()); r.setUrl(dto.url()); r.setDisplayOrder(order);
        resourceRepository.save(r);
        return new ResourceDto(r.getId(), r.getLabel(), r.getUrl(), r.getDisplayOrder());
    }

    @Transactional
    public ResourceDto updateResource(Long userId, Long id, ResourceDto dto) {
        Resource r = resourceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        if (dto.label() != null) r.setLabel(dto.label());
        if (dto.url() != null) r.setUrl(dto.url());
        resourceRepository.save(r);
        return new ResourceDto(r.getId(), r.getLabel(), r.getUrl(), r.getDisplayOrder());
    }

    @Transactional
    public void deleteResource(Long userId, Long id) {
        Resource r = resourceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        resourceRepository.delete(r);
    }

    // ── Exercises ──────────────────────────────────────────────────────────────

    public List<ExerciseDto> listExercises(Long userId, String slug) {
        Topic topic = studyService.requireOwned(userId, slug);
        return exerciseRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(e -> new ExerciseDto(e.getId(), e.getTitle(), e.getRepoUrl(), e.isDone(), e.getDisplayOrder())).toList();
    }

    @Transactional
    public ExerciseDto createExercise(Long userId, String slug, ExerciseDto dto) {
        Topic topic = studyService.requireOwned(userId, slug);
        int order = exerciseRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).size();
        Exercise e = new Exercise();
        e.setUserId(userId); e.setTopicId(topic.getId());
        e.setTitle(dto.title()); e.setRepoUrl(dto.repoUrl()); e.setDone(dto.done()); e.setDisplayOrder(order);
        exerciseRepository.save(e);
        return new ExerciseDto(e.getId(), e.getTitle(), e.getRepoUrl(), e.isDone(), e.getDisplayOrder());
    }

    @Transactional
    public ExerciseDto updateExercise(Long userId, Long id, ExerciseDto dto) {
        Exercise e = exerciseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        if (dto.title() != null) e.setTitle(dto.title());
        if (dto.repoUrl() != null) e.setRepoUrl(dto.repoUrl());
        e.setDone(dto.done());
        exerciseRepository.save(e);
        return new ExerciseDto(e.getId(), e.getTitle(), e.getRepoUrl(), e.isDone(), e.getDisplayOrder());
    }

    @Transactional
    public void deleteExercise(Long userId, Long id) {
        Exercise e = exerciseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        exerciseRepository.delete(e);
    }

    // ── Questions ──────────────────────────────────────────────────────────────

    public List<QuestionDto> listQuestions(Long userId, String slug) {
        Topic topic = studyService.requireOwned(userId, slug);
        return questionRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).stream()
                .map(q -> new QuestionDto(q.getId(), q.getText(), q.getDisplayOrder())).toList();
    }

    @Transactional
    public QuestionDto createQuestion(Long userId, String slug, QuestionDto dto) {
        Topic topic = studyService.requireOwned(userId, slug);
        int order = questionRepository.findByUserIdAndTopicIdOrderByDisplayOrderAsc(userId, topic.getId()).size();
        Question q = new Question();
        q.setUserId(userId); q.setTopicId(topic.getId()); q.setText(dto.text()); q.setDisplayOrder(order);
        questionRepository.save(q);
        return new QuestionDto(q.getId(), q.getText(), q.getDisplayOrder());
    }

    @Transactional
    public void deleteQuestion(Long userId, Long id) {
        Question q = questionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        questionRepository.delete(q);
    }
}

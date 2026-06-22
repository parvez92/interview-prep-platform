package com.interview.prep.platform.backend_core.star;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.star.dto.StarStoryDto;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StarService {

    private final StarStoryRepository repo;
    private final ObjectMapper objectMapper;

    public List<StarStoryDto> list(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    public StarStoryDto get(Long userId, Long id) {
        return toDto(requireOwned(userId, id));
    }

    @Transactional
    @SneakyThrows
    public StarStoryDto create(Long userId, StarStoryDto dto) {
        StarStory story = new StarStory();
        story.setUserId(userId);
        apply(story, dto);
        repo.save(story);
        return toDto(story);
    }

    @Transactional
    public StarStoryDto update(Long userId, Long id, StarStoryDto dto) {
        StarStory story = requireOwned(userId, id);
        apply(story, dto);
        story.setUpdatedAt(Instant.now());
        repo.save(story);
        return toDto(story);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(requireOwned(userId, id));
    }

    @SneakyThrows
    private void apply(StarStory s, StarStoryDto dto) {
        s.setTitle(dto.title()); s.setSituation(dto.situation());
        s.setTask(dto.task()); s.setAction(dto.action()); s.setResult(dto.result());
        s.setTagsJson(dto.tags() != null ? objectMapper.writeValueAsString(dto.tags()) : null);
    }

    @SneakyThrows
    private StarStoryDto toDto(StarStory s) {
        List<String> tags = s.getTagsJson() != null
                ? objectMapper.readValue(s.getTagsJson(), new TypeReference<>() {}) : List.of();
        return new StarStoryDto(s.getId(), s.getTitle(), s.getSituation(),
                s.getTask(), s.getAction(), s.getResult(), tags);
    }

    private StarStory requireOwned(Long userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}

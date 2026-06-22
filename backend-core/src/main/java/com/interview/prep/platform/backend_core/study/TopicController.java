package com.interview.prep.platform.backend_core.study;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.study.dto.CreateTopicDto;
import com.interview.prep.platform.backend_core.study.dto.TopicDetailDto;
import com.interview.prep.platform.backend_core.study.dto.UpdateTopicDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final StudyService studyService;

    @GetMapping("/{slug}")
    public ResponseEntity<TopicDetailDto> getTopic(@CurrentUser Long userId, @PathVariable String slug) {
        return ResponseEntity.ok(studyService.getTopicDetail(userId, slug));
    }

    @PostMapping
    public ResponseEntity<TopicDetailDto> createTopic(@CurrentUser Long userId,
                                                      @Valid @RequestBody CreateTopicDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studyService.createTopic(userId, dto));
    }

    @PatchMapping("/{slug}")
    public ResponseEntity<TopicDetailDto> updateTopic(@CurrentUser Long userId,
                                                      @PathVariable String slug,
                                                      @RequestBody UpdateTopicDto dto) {
        return ResponseEntity.ok(studyService.updateTopic(userId, slug, dto));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> deleteTopic(@CurrentUser Long userId, @PathVariable String slug) {
        studyService.deleteTopic(userId, slug);
        return ResponseEntity.noContent().build();
    }
}

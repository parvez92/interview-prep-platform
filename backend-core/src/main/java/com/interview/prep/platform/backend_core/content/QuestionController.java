package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.content.dto.QuestionDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final ContentService contentService;

    @GetMapping("/api/topics/{slug}/questions")
    public ResponseEntity<List<QuestionDto>> list(@CurrentUser Long userId, @PathVariable String slug) {
        return ResponseEntity.ok(contentService.listQuestions(userId, slug));
    }

    @PostMapping("/api/topics/{slug}/questions")
    public ResponseEntity<QuestionDto> create(@CurrentUser Long userId, @PathVariable String slug,
                                              @Valid @RequestBody QuestionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.createQuestion(userId, slug, dto));
    }

    @DeleteMapping("/api/questions/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        contentService.deleteQuestion(userId, id);
        return ResponseEntity.noContent().build();
    }
}

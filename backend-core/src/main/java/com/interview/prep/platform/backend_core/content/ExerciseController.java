package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.content.dto.ExerciseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ExerciseController {

    private final ContentService contentService;

    @GetMapping("/api/topics/{slug}/exercises")
    public ResponseEntity<List<ExerciseDto>> list(@CurrentUser Long userId, @PathVariable String slug) {
        return ResponseEntity.ok(contentService.listExercises(userId, slug));
    }

    @PostMapping("/api/topics/{slug}/exercises")
    public ResponseEntity<ExerciseDto> create(@CurrentUser Long userId, @PathVariable String slug,
                                              @Valid @RequestBody ExerciseDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.createExercise(userId, slug, dto));
    }

    @PatchMapping("/api/exercises/{id}")
    public ResponseEntity<ExerciseDto> update(@CurrentUser Long userId, @PathVariable Long id,
                                              @RequestBody ExerciseDto dto) {
        return ResponseEntity.ok(contentService.updateExercise(userId, id, dto));
    }

    @DeleteMapping("/api/exercises/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        contentService.deleteExercise(userId, id);
        return ResponseEntity.noContent().build();
    }
}

package com.interview.prep.platform.backend_core.star;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.star.dto.StarStoryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/star-stories")
@RequiredArgsConstructor
public class StarController {

    private final StarService starService;

    @GetMapping
    public ResponseEntity<List<StarStoryDto>> list(@CurrentUser Long userId) {
        return ResponseEntity.ok(starService.list(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StarStoryDto> get(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(starService.get(userId, id));
    }

    @PostMapping
    public ResponseEntity<StarStoryDto> create(@CurrentUser Long userId, @Valid @RequestBody StarStoryDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(starService.create(userId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StarStoryDto> update(@CurrentUser Long userId, @PathVariable Long id,
                                               @Valid @RequestBody StarStoryDto dto) {
        return ResponseEntity.ok(starService.update(userId, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        starService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

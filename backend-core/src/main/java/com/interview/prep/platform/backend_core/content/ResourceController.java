package com.interview.prep.platform.backend_core.content;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.content.dto.ResourceDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ResourceController {

    private final ContentService contentService;

    @GetMapping("/api/topics/{slug}/resources")
    public ResponseEntity<List<ResourceDto>> list(@CurrentUser Long userId, @PathVariable String slug) {
        return ResponseEntity.ok(contentService.listResources(userId, slug));
    }

    @PostMapping("/api/topics/{slug}/resources")
    public ResponseEntity<ResourceDto> create(@CurrentUser Long userId, @PathVariable String slug,
                                              @Valid @RequestBody ResourceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contentService.createResource(userId, slug, dto));
    }

    @PatchMapping("/api/resources/{id}")
    public ResponseEntity<ResourceDto> update(@CurrentUser Long userId, @PathVariable Long id,
                                              @RequestBody ResourceDto dto) {
        return ResponseEntity.ok(contentService.updateResource(userId, id, dto));
    }

    @DeleteMapping("/api/resources/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable Long id) {
        contentService.deleteResource(userId, id);
        return ResponseEntity.noContent().build();
    }
}

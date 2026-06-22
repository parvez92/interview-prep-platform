package com.interview.prep.platform.backend_core.preppack;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PrepPackController {

    private final PrepPackService prepPackService;

    @PostMapping("/api/interviews/{id}/prep-pack")
    public ResponseEntity<Map<String, Object>> createOrGet(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(prepPackService.getOrCreate(userId, id));
    }

    @GetMapping("/api/interviews/{id}/prep-pack")
    public ResponseEntity<Map<String, Object>> get(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(prepPackService.getOrCreate(userId, id));
    }
}

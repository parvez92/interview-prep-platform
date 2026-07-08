package com.interview.prep.platform.backend_core.mock;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mocks")
@RequiredArgsConstructor
public class MockController {

    private final MockService mockService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@CurrentUser Long userId,
                                                     @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mockService.start(userId, request));
    }

    @PostMapping("/{id}/turn")
    public ResponseEntity<Map<String, Object>> turn(@CurrentUser Long userId,
                                                    @PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        String answer = body.get("answer") instanceof String s ? s : null;
        return ResponseEntity.ok(mockService.turn(userId, id, answer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(mockService.get(userId, id));
    }
}

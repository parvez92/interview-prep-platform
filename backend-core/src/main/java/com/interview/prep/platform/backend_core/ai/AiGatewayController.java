package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiClient aiClient;
    private final TopicRepository topicRepository;

    @GetMapping("/guide/{slug}")
    public ResponseEntity<Map<String, Object>> guide(@CurrentUser Long userId, @PathVariable String slug) {
        Map<String, Object> result = aiClient.get(userId, "/ai/guide/" + slug, "guide");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> mock(@CurrentUser Long userId,
                                                    @RequestBody Map<String, Object> body) {
        Map<String, Object> result = aiClient.post(userId, "/ai/mock", body, "mock");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@CurrentUser Long userId,
                                                       @RequestBody Map<String, Object> body) {
        Map<String, Object> result = aiClient.post(userId, "/ai/analyze", body, "analyze");
        return ResponseEntity.ok(result);
    }
}

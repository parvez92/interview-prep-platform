package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final AiClient aiClient;
    private final TopicRepository topicRepository;
    private final ReviewFlagRepository flagRepository;

    @PostMapping("/rebalance")
    public ResponseEntity<Map<String, Object>> rebalance(@CurrentUser Long userId) {
        long done = topicRepository.countByUserIdAndStatus(userId, "done");
        long total = topicRepository.countByUserId(userId);
        long flags = flagRepository.countByUserIdAndResolved(userId, false);
        Map<String, Object> payload = Map.of(
                "progress", Map.of("done", done, "total", total),
                "openFlags", flags);
        Map<String, Object> result = aiClient.post(userId, "/ai/agents/coach", payload, "plan-rebalance");
        return ResponseEntity.ok(result);
    }
}

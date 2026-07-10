package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.ai.PlanAuditService;
import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final AiClient aiClient;
    private final TopicRepository topicRepository;
    private final ReviewFlagRepository flagRepository;
    private final PlanAuditService planAuditService;

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

    /**
     * Checklist coverage + week-budget feasibility for the plan under review.
     * Deterministic and free — no model call. Body may carry the draft plan
     * (`{"phases":[…]}`); without it the committed plan is audited.
     */
    @PostMapping("/audit")
    @SuppressWarnings("unchecked")
    public ResponseEntity<PlanAuditService.Audit> audit(@CurrentUser Long userId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        List<Map<String, Object>> phases = body != null && body.get("phases") instanceof List<?> raw
                ? (List<Map<String, Object>>) raw : null;
        return ResponseEntity.ok(planAuditService.audit(userId, phases));
    }
}

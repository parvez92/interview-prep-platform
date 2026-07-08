package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiClient aiClient;
    private final AiGatewayService aiGatewayService;
    private final SeedPipelineService seedPipelineService;
    private final BudgetGuard budgetGuard;

    @PostMapping("/guide")
    public ResponseEntity<Map<String, Object>> guide(
            @CurrentUser Long userId,
            @RequestBody Map<String, Object> body) {
        String slug = (String) body.get("slug");
        String tab  = body.containsKey("tab") ? (String) body.get("tab") : "overview";
        if (slug == null || slug.isBlank())
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.BAD_REQUEST, "slug is required");
        return ResponseEntity.ok(aiGatewayService.generateForTab(userId, slug, tab));
    }

    @PostMapping("/seed-plan")
    public ResponseEntity<Map<String, Object>> seedPlan(@CurrentUser Long userId,
                                                        @RequestParam(value = "provider", required = false) String provider) {
        // desktop = synchronous copy-paste prompt; anything else runs the async pipeline
        String effective = provider != null ? provider : budgetGuard.resolveProvider(userId);
        if ("desktop".equalsIgnoreCase(effective)) {
            return ResponseEntity.ok(aiGatewayService.seedPlan(userId, "desktop"));
        }
        return ResponseEntity.ok(seedPipelineService.start(userId));
    }

    @GetMapping("/seed-plan/status")
    public ResponseEntity<Map<String, Object>> seedPlanStatus(@CurrentUser Long userId) {
        return ResponseEntity.ok(seedPipelineService.status(userId));
    }

    @PostMapping("/seed-plan/manual")
    public ResponseEntity<Map<String, Object>> seedPlanManual(@CurrentUser Long userId,
                                                              @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiGatewayService.seedPlanManual(userId, body));
    }

    @PostMapping("/mock")
    public ResponseEntity<Map<String, Object>> mock(@CurrentUser Long userId,
                                                    @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiClient.post(userId, "/ai/mock", body, "mock"));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@CurrentUser Long userId,
                                                       @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiClient.post(userId, "/ai/analyze", body, "analyze"));
    }
}

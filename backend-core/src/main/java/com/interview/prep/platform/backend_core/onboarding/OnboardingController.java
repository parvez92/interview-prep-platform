package com.interview.prep.platform.backend_core.onboarding;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.onboarding.dto.ConfirmProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> uploadResume(
            @CurrentUser Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Override-Provider", required = false) String providerOverride,
            @RequestHeader(value = "X-Ollama-Url",        required = false) String ollamaUrl,
            @RequestHeader(value = "X-Ollama-Model",      required = false) String ollamaModel) {
        return ResponseEntity.ok(onboardingService.uploadResume(userId, file, providerOverride, ollamaUrl, ollamaModel));
    }

    @PostMapping("/resume/manual")
    public ResponseEntity<Map<String, Object>> uploadResumeManual(
            @CurrentUser Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("parsedJson") String parsedJson) {
        return ResponseEntity.ok(onboardingService.uploadResumeManual(userId, file, parsedJson));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> confirmProfile(@CurrentUser Long userId,
                                                              @RequestBody ConfirmProfileDto dto) {
        return ResponseEntity.ok(onboardingService.confirmProfile(userId, dto));
    }

    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> generatePlan(
            @CurrentUser Long userId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(onboardingService.generatePlan(userId, body));
    }

    @PostMapping("/plan/manual")
    public ResponseEntity<Map<String, Object>> submitPlanManual(@CurrentUser Long userId,
                                                                @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(onboardingService.submitPlanManual(userId, body));
    }

    @PutMapping("/plan/commit")
    public ResponseEntity<Map<String, Object>> commitPlan(@CurrentUser Long userId,
                                                          @RequestBody Map<String, Object> plan) {
        return ResponseEntity.ok(onboardingService.commitPlan(userId, plan));
    }
}

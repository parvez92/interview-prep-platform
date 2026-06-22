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
    public ResponseEntity<Map<String, Object>> uploadResume(@CurrentUser Long userId,
                                                            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(onboardingService.uploadResume(userId, file));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> confirmProfile(@CurrentUser Long userId,
                                                              @RequestBody ConfirmProfileDto dto) {
        return ResponseEntity.ok(onboardingService.confirmProfile(userId, dto));
    }

    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> generatePlan(@CurrentUser Long userId) {
        return ResponseEntity.ok(onboardingService.generatePlan(userId));
    }

    @PutMapping("/plan/commit")
    public ResponseEntity<Map<String, Object>> commitPlan(@CurrentUser Long userId,
                                                          @RequestBody Map<String, Object> plan) {
        return ResponseEntity.ok(onboardingService.commitPlan(userId, plan));
    }
}

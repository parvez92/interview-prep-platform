package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.ai.dto.UsageSummaryDto;
import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    @GetMapping
    public ResponseEntity<UsageSummaryDto> summary(@CurrentUser Long userId) {
        return ResponseEntity.ok(usageService.getSummary(userId));
    }
}

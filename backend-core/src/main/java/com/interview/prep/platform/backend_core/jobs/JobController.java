package com.interview.prep.platform.backend_core.jobs;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.ingestion.GmailSyncService;
import com.interview.prep.platform.backend_core.jobs.dto.JobAlertDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    // Gmail beans only exist when app.gmail.enabled=true
    private final ObjectProvider<GmailSyncService> gmailSyncService;

    @GetMapping
    public ResponseEntity<List<JobAlertDto>> list(@CurrentUser Long userId) {
        return ResponseEntity.ok(jobService.list(userId));
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(@CurrentUser Long userId) {
        GmailSyncService service = gmailSyncService.getIfAvailable();
        if (service == null) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Gmail sync is not configured — set app.gmail.enabled and the Vault credentials.");
        }
        return ResponseEntity.ok(service.syncNow(userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<JobAlertDto> patch(@CurrentUser Long userId, @PathVariable Long id,
                                             @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(jobService.patch(userId, id, patch));
    }
}

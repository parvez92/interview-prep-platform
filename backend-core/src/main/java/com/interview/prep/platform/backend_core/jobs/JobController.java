package com.interview.prep.platform.backend_core.jobs;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.jobs.dto.JobAlertDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping
    public ResponseEntity<List<JobAlertDto>> list(@CurrentUser Long userId) {
        return ResponseEntity.ok(jobService.list(userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<JobAlertDto> patch(@CurrentUser Long userId, @PathVariable Long id,
                                             @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(jobService.patch(userId, id, patch));
    }
}

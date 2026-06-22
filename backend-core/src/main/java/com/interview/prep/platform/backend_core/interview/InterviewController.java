package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.interview.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping
    public ResponseEntity<List<InterviewSummaryDto>> list(@CurrentUser Long userId) {
        return ResponseEntity.ok(interviewService.listInterviews(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewDetailDto> get(@CurrentUser Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(interviewService.getInterview(userId, id));
    }

    @PostMapping
    public ResponseEntity<InterviewSummaryDto> create(@CurrentUser Long userId,
                                                      @Valid @RequestBody CreateInterviewDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.createInterview(userId, dto));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InterviewSummaryDto> update(@CurrentUser Long userId, @PathVariable Long id,
                                                      @RequestBody UpdateInterviewDto dto) {
        return ResponseEntity.ok(interviewService.updateInterview(userId, id, dto));
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<InterviewDetailDto> logQuestion(@CurrentUser Long userId, @PathVariable Long id,
                                                          @Valid @RequestBody LogQuestionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewService.logQuestion(userId, id, dto));
    }

    @PatchMapping("/questions/{questionId}")
    public ResponseEntity<InterviewDetailDto> updateQuestion(@CurrentUser Long userId,
                                                             @PathVariable Long questionId,
                                                             @Valid @RequestBody LogQuestionDto dto) {
        return ResponseEntity.ok(interviewService.updateQuestion(userId, questionId, dto));
    }

    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@CurrentUser Long userId, @PathVariable Long questionId) {
        interviewService.deleteQuestion(userId, questionId);
        return ResponseEntity.noContent().build();
    }
}

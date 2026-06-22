package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.review.dto.ReviewItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/today")
    public ResponseEntity<List<ReviewItemDto>> today(@CurrentUser Long userId) {
        return ResponseEntity.ok(reviewService.getTodayReview(userId));
    }

    @PostMapping("/{slug}/done")
    public ResponseEntity<Void> markDone(@CurrentUser Long userId, @PathVariable String slug) {
        reviewService.markDone(userId, slug);
        return ResponseEntity.ok().build();
    }
}

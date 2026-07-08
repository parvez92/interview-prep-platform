package com.interview.prep.platform.backend_core.user;

import com.interview.prep.platform.backend_core.common.security.CurrentUser;
import com.interview.prep.platform.backend_core.user.dto.MeResponse;
import com.interview.prep.platform.backend_core.user.dto.PatchMeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<MeResponse> getMe(@CurrentUser Long userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PatchMapping
    public ResponseEntity<MeResponse> patchMe(@CurrentUser Long userId, @RequestBody PatchMeRequest req) {
        return ResponseEntity.ok(userService.patchMe(userId, req));
    }
}

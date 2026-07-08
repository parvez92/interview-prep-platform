package com.interview.prep.platform.backend_core.user.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    private final long expiresIn;
}

package com.interview.prep.platform.backend_core.common.error;

import java.time.Instant;

public record ErrorResponse(String code, String message, Instant timestamp) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(code.name(), message, Instant.now());
    }
}

package com.interview.prep.platform.backend_core.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public ApiException(ErrorCode errorCode, HttpStatus status) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.status = status;
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}

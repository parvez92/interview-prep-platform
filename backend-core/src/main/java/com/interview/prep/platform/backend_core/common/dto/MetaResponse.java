package com.interview.prep.platform.backend_core.common.dto;

public record MetaResponse<T>(T data, Object meta) {
}

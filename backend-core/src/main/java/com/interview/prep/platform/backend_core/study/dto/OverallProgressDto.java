package com.interview.prep.platform.backend_core.study.dto;

import java.util.List;

public record OverallProgressDto(
        int overallPct,
        int streak,
        List<TrackReadinessDto> tracks,
        long flaggedCount
) {
    public record TrackReadinessDto(String name, int readiness) {}
}

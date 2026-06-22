package com.interview.prep.platform.backend_core.study;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReadinessCalculator {

    /**
     * readiness = 100 * (done/total) * (1 − 0.4*openFlags/total) * avgConfidence/100
     * Clamped 0–100.
     */
    public int calculate(List<Topic> topics, long openFlagCount) {
        int total = topics.size();
        if (total == 0) return 0;
        long done = topics.stream().filter(t -> "done".equals(t.getStatus())).count();
        if (done == 0) return 0;
        double avgConf = topics.stream()
                .filter(t -> "done".equals(t.getStatus()))
                .mapToInt(t -> t.getConfidence() != null ? t.getConfidence() : 80)
                .average()
                .orElse(80.0);
        double raw = 100.0
                * ((double) done / total)
                * (1 - 0.4 * ((double) openFlagCount / total))
                * (avgConf / 100.0);
        return (int) Math.min(100, Math.max(0, raw));
    }
}

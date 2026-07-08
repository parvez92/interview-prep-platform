package com.interview.prep.platform.backend_core.user.dto;

import java.math.BigDecimal;

public record PatchMeRequest(String displayName, SettingsPatch settings) {

    public record SettingsPatch(
            String llmProvider,
            String modelStrong,
            String modelCheap,
            String ollamaUrl,
            Double monthlyBudgetUsd
    ) {}
}

package com.interview.prep.platform.backend_core.user.dto;

import com.interview.prep.platform.backend_core.user.AppUser;
import com.interview.prep.platform.backend_core.user.UserSettings;

import java.math.BigDecimal;

public record MeResponse(UserDto user, SettingsDto settings, boolean onboarded) {

    public static MeResponse from(AppUser user, UserSettings settings) {
        return new MeResponse(
                new UserDto(user.getEmail(), user.getDisplayName()),
                new SettingsDto(
                        settings.getLlmProvider(),
                        settings.getLlmModelStrong(),
                        settings.getLlmModelCheap(),
                        settings.getOllamaUrl(),
                        settings.getMonthlyBudgetUsd()
                ),
                settings.isOnboarded()
        );
    }

    public record UserDto(String email, String displayName) {}

    public record SettingsDto(
            String llmProvider,
            String modelStrong,
            String modelCheap,
            String ollamaUrl,
            BigDecimal monthlyBudgetUsd
    ) {}
}

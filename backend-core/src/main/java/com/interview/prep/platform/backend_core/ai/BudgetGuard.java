package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BudgetGuard {

    public enum Tier { STRONG, CHEAP }

    private final UsageService usageService;
    private final UserSettingsRepository userSettingsRepository;

    public Tier resolveTier(Long userId) {
        return usageService.isOverBudget(userId) ? Tier.CHEAP : Tier.STRONG;
    }

    public String resolveProvider(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(UserSettings::getLlmProvider)
                .filter(p -> p != null && !p.isBlank())
                .orElse("anthropic");
    }

    private static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";

    public String resolveModel(Long userId) {
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        if (settings == null) return DEFAULT_MODEL;
        Tier tier = resolveTier(userId);
        String model = tier == Tier.STRONG
                ? settings.getLlmModelStrong()
                : settings.getLlmModelCheap();
        // fresh settings rows have no model configured yet — don't send a blank X-Model
        return model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    public String resolveOllamaUrl(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .map(UserSettings::getOllamaUrl)
                .orElse(null);
    }

    public boolean isBudgetWarning(Long userId) {
        return usageService.isOverBudget(userId);
    }
}

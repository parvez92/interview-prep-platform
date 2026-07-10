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

    // Tier-aware fallbacks for fresh settings rows with no model configured.
    // The strong default must support adaptive thinking — the ai-service enables it
    // for the quality-critical passes (plan/depth), and Haiku 4.5 rejects it.
    private static final String DEFAULT_MODEL_STRONG = "claude-sonnet-5";
    private static final String DEFAULT_MODEL_CHEAP = "claude-haiku-4-5-20251001";

    public String resolveModel(Long userId) {
        Tier tier = resolveTier(userId);
        String fallback = tier == Tier.STRONG ? DEFAULT_MODEL_STRONG : DEFAULT_MODEL_CHEAP;
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        if (settings == null) return fallback;
        String model = tier == Tier.STRONG
                ? settings.getLlmModelStrong()
                : settings.getLlmModelCheap();
        // fresh settings rows have no model configured yet — don't send a blank X-Model
        return model != null && !model.isBlank() ? model : fallback;
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

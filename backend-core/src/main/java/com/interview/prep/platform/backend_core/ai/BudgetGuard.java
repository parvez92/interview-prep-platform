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

    public String resolveModel(Long userId) {
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        if (settings == null) return "claude-haiku-4-5-20251001";
        Tier tier = resolveTier(userId);
        return tier == Tier.STRONG
                ? settings.getLlmModelStrong()
                : settings.getLlmModelCheap();
    }

    public boolean isBudgetWarning(Long userId) {
        return usageService.isOverBudget(userId);
    }
}

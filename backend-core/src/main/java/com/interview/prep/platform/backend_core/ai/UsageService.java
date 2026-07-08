package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.ai.dto.UsageSummaryDto;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsageService {

    private final UsageLogRepository usageLogRepository;
    private final UserSettingsRepository userSettingsRepository;

    public BigDecimal currentMonthCost(Long userId) {
        Instant startOfMonth = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return usageLogRepository.sumCostSince(userId, startOfMonth);
    }

    public boolean isOverBudget(Long userId) {
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        if (settings == null || settings.getMonthlyBudgetUsd() == null) return false;
        return currentMonthCost(userId).compareTo(settings.getMonthlyBudgetUsd()) >= 0;
    }

    public UsageSummaryDto getSummary(Long userId) {
        BigDecimal monthCost = currentMonthCost(userId);
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        BigDecimal budget = (settings != null && settings.getMonthlyBudgetUsd() != null)
                ? settings.getMonthlyBudgetUsd() : BigDecimal.valueOf(20);
        BigDecimal remaining = budget.subtract(monthCost).max(BigDecimal.ZERO);
        boolean warning = monthCost.compareTo(budget.multiply(BigDecimal.valueOf(0.8))) >= 0;
        return new UsageSummaryDto(monthCost, budget, remaining, warning);
    }

    @Transactional
    public UsageLog record(Long userId, String feature, String provider, String model,
                           int promptTokens, int completionTokens, BigDecimal costUsd) {
        UsageLog log = new UsageLog();
        log.setUserId(userId); log.setFeature(feature); log.setProvider(provider);
        log.setModel(model); log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens); log.setCostUsd(costUsd);
        return usageLogRepository.save(log);
    }
}

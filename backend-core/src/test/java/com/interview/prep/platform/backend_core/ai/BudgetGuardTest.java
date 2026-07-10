package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetGuardTest {

    @Mock UsageService usageService;
    @Mock UserSettingsRepository userSettingsRepository;
    @InjectMocks BudgetGuard guard;

    @Test
    void withinBudget_returnsStrongTier() {
        when(usageService.isOverBudget(1L)).thenReturn(false);
        assertThat(guard.resolveTier(1L)).isEqualTo(BudgetGuard.Tier.STRONG);
    }

    @Test
    void overBudget_returnsCheapTier() {
        when(usageService.isOverBudget(1L)).thenReturn(true);
        assertThat(guard.resolveTier(1L)).isEqualTo(BudgetGuard.Tier.CHEAP);
    }

    @Test
    void withinBudget_usesStrongModel() {
        UserSettings settings = new UserSettings();
        settings.setLlmModelStrong("claude-opus-4-8");
        settings.setLlmModelCheap("claude-haiku-4-5-20251001");
        settings.setMonthlyBudgetUsd(new BigDecimal("20.00"));
        when(usageService.isOverBudget(1L)).thenReturn(false);
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThat(guard.resolveModel(1L)).isEqualTo("claude-opus-4-8");
    }

    @Test
    void overBudget_usesCheapModel() {
        UserSettings settings = new UserSettings();
        settings.setLlmModelStrong("claude-opus-4-8");
        settings.setLlmModelCheap("claude-haiku-4-5-20251001");
        settings.setMonthlyBudgetUsd(new BigDecimal("20.00"));
        when(usageService.isOverBudget(1L)).thenReturn(true);
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThat(guard.resolveModel(1L)).isEqualTo("claude-haiku-4-5-20251001");
    }

    @Test
    void noSettings_withinBudget_fallsBackToStrongDefault() {
        when(usageService.isOverBudget(1L)).thenReturn(false);
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(guard.resolveModel(1L)).isEqualTo("claude-sonnet-5");
    }

    @Test
    void noSettings_overBudget_fallsBackToHaiku() {
        when(usageService.isOverBudget(1L)).thenReturn(true);
        when(userSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(guard.resolveModel(1L)).isEqualTo("claude-haiku-4-5-20251001");
    }

    @Test
    void budgetWarning_trueWhenOverBudget() {
        when(usageService.isOverBudget(1L)).thenReturn(true);
        assertThat(guard.isBudgetWarning(1L)).isTrue();
    }
}

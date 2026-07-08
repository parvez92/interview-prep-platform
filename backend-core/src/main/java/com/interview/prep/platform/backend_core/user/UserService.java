package com.interview.prep.platform.backend_core.user;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.user.dto.MeResponse;
import com.interview.prep.platform.backend_core.user.dto.PatchMeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final UserSettingsRepository settingsRepository;

    public MeResponse getMe(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        UserSettings settings = settingsRepository.findByUserId(userId)
                .orElseGet(UserSettings::new);
        return MeResponse.from(user, settings);
    }

    @Transactional
    public MeResponse patchMe(Long userId, PatchMeRequest req) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));

        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName());
            userRepository.save(user);
        }

        if (req.settings() != null) {
            UserSettings s = settingsRepository.findByUserId(userId).orElseGet(() -> {
                UserSettings n = new UserSettings(); n.setUserId(userId); return n;
            });
            PatchMeRequest.SettingsPatch p = req.settings();
            if (p.llmProvider()      != null) s.setLlmProvider(p.llmProvider());
            if (p.modelStrong()      != null) s.setLlmModelStrong(p.modelStrong());
            if (p.modelCheap()       != null) s.setLlmModelCheap(p.modelCheap());
            if (p.ollamaUrl()        != null) s.setOllamaUrl(p.ollamaUrl().isBlank() ? null : p.ollamaUrl());
            if (p.monthlyBudgetUsd() != null) s.setMonthlyBudgetUsd(BigDecimal.valueOf(p.monthlyBudgetUsd()));
            settingsRepository.save(s);
        }

        return getMe(userId);
    }
}

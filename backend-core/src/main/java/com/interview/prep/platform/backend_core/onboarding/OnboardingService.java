package com.interview.prep.platform.backend_core.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.onboarding.dto.ConfirmProfileDto;
import com.interview.prep.platform.backend_core.storage.FileStore;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final ResumeProfileRepository resumeProfileRepository;
    private final FileStore fileStore;
    private final AiClient aiClient;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    public Map<String, Object> uploadResume(Long userId, MultipartFile file) {
        String ref = fileStore.store(file, "resumes");
        ResumeProfile profile = resumeProfileRepository.findByUserId(userId)
                .orElseGet(ResumeProfile::new);
        profile.setUserId(userId);
        profile.setFileRef(ref);
        resumeProfileRepository.save(profile);

        // Delegate parsing to AI service
        Map<String, Object> result = aiClient.post(userId, "/ai/resume/parse",
                Map.of("fileRef", ref), "resume-parse");

        String parsedJson = objectMapper.writeValueAsString(result);
        profile.setParsedJson(parsedJson);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);
        return result;
    }

    @Transactional
    @SneakyThrows
    public Map<String, Object> confirmProfile(Long userId, ConfirmProfileDto dto) {
        ResumeProfile profile = requireProfile(userId);
        String json = objectMapper.writeValueAsString(dto.profile());
        profile.setConfirmedJson(json);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(UserSettings::new);
        settings.setUserId(userId);
        if (dto.targetRole() != null) settings.setTargetRole(dto.targetRole());
        if (dto.targetLevel() != null) settings.setTargetLevel(dto.targetLevel());
        if (dto.prepWeeks() != null) settings.setPrepWeeks(dto.prepWeeks());
        userSettingsRepository.save(settings);
        return Map.of("status", "confirmed");
    }

    @Transactional
    public Map<String, Object> generatePlan(Long userId) {
        ResumeProfile profile = requireProfile(userId);
        if (profile.getConfirmedJson() == null) {
            throw new ApiException(ErrorCode.PLAN_NOT_COMMITTED, HttpStatus.CONFLICT);
        }
        return aiClient.post(userId, "/ai/plan/generate",
                Map.of("profileJson", profile.getConfirmedJson()), "plan-generate");
    }

    @Transactional
    public Map<String, Object> commitPlan(Long userId, Map<String, Object> plan) {
        ResumeProfile profile = requireProfile(userId);
        profile.setPlanCommitted(true);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);

        UserSettings settings = userSettingsRepository.findByUserId(userId).orElseGet(UserSettings::new);
        settings.setUserId(userId);
        settings.setOnboarded(true);
        userSettingsRepository.save(settings);
        return Map.of("status", "committed");
    }

    private ResumeProfile requireProfile(Long userId) {
        return resumeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}

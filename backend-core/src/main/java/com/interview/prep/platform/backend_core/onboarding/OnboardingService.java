package com.interview.prep.platform.backend_core.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.content.Exercise;
import com.interview.prep.platform.backend_core.content.ExerciseRepository;
import com.interview.prep.platform.backend_core.content.Question;
import com.interview.prep.platform.backend_core.content.QuestionRepository;
import com.interview.prep.platform.backend_core.content.Resource;
import com.interview.prep.platform.backend_core.content.ResourceRepository;
import com.interview.prep.platform.backend_core.onboarding.dto.ConfirmProfileDto;
import com.interview.prep.platform.backend_core.storage.FileStore;
import com.interview.prep.platform.backend_core.study.Phase;
import com.interview.prep.platform.backend_core.study.PhaseRepository;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.study.Week;
import com.interview.prep.platform.backend_core.study.WeekRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final ResumeProfileRepository resumeProfileRepository;
    private final FileStore fileStore;
    private final AiClient aiClient;
    private final UserSettingsRepository userSettingsRepository;
    private final PhaseRepository phaseRepository;
    private final WeekRepository weekRepository;
    private final TopicRepository topicRepository;
    private final ResourceRepository resourceRepository;
    private final QuestionRepository questionRepository;
    private final ExerciseRepository exerciseRepository;
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    public Map<String, Object> uploadResume(Long userId, MultipartFile file,
                                             String providerOverride, String ollamaUrl, String ollamaModel) {
        // Save Ollama settings early so AI call and future calls use the right model
        if ("ollama".equalsIgnoreCase(providerOverride)) {
            UserSettings settings = userSettingsRepository.findByUserId(userId).orElseGet(UserSettings::new);
            settings.setUserId(userId);
            settings.setLlmProvider("ollama");
            if (ollamaUrl != null && !ollamaUrl.isBlank())   settings.setOllamaUrl(ollamaUrl);
            if (ollamaModel != null && !ollamaModel.isBlank()) {
                settings.setLlmModelStrong(ollamaModel);
                settings.setLlmModelCheap(ollamaModel);
            }
            userSettingsRepository.save(settings);
        }

        String rawText = extractText(file);
        String ref = fileStore.store(file, "resumes");
        ResumeProfile profile = resumeProfileRepository.findByUserId(userId)
                .orElseGet(ResumeProfile::new);
        profile.setUserId(userId);
        profile.setFileRef(ref);
        resumeProfileRepository.save(profile);

        Map<String, Object> result = aiClient.post(userId, "/ai/parse-resume",
                Map.of("raw_text", rawText), "resume-parse", providerOverride, ollamaUrl, ollamaModel);

        String parsedJson = objectMapper.writeValueAsString(result);
        profile.setParsedJson(parsedJson);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);
        return result;
    }

    @Transactional
    @SneakyThrows
    public Map<String, Object> uploadResumeManual(Long userId, MultipartFile file, String parsedJson) {
        String ref = fileStore.store(file, "resumes");
        ResumeProfile profile = resumeProfileRepository.findByUserId(userId)
                .orElseGet(ResumeProfile::new);
        profile.setUserId(userId);
        profile.setFileRef(ref);
        profile.setParsedJson(parsedJson);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);

        Map<String, Object> parsed = objectMapper.readValue(parsedJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        return Map.of("result", parsed, "meta", Map.of("model", "desktop", "cached", false, "tokens", 0, "cost", 0.0));
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
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public Map<String, Object> generatePlan(Long userId, Map<String, Object> requestBody) {
        // Save provider/model/budget + targets from onboarding form BEFORE calling AI
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElseGet(UserSettings::new);
        settings.setUserId(userId);
        if (requestBody != null) {
            String provider = (String) requestBody.get("llmProvider");
            if (provider != null && !provider.isBlank()) settings.setLlmProvider(provider);
            String ollamaUrl = (String) requestBody.get("ollamaUrl");
            if (ollamaUrl != null && !ollamaUrl.isBlank()) settings.setOllamaUrl(ollamaUrl);
            String ollamaModel = (String) requestBody.get("ollamaModel");
            if (ollamaModel != null && !ollamaModel.isBlank()) {
                settings.setLlmModelStrong(ollamaModel);
                settings.setLlmModelCheap(ollamaModel);
            }
            Object budget = requestBody.get("monthlyBudgetUsd");
            if (budget instanceof Number n) settings.setMonthlyBudgetUsd(java.math.BigDecimal.valueOf(n.doubleValue()));
            String targetRole = (String) requestBody.get("targetRole");
            if (targetRole != null && !targetRole.isBlank()) settings.setTargetRole(targetRole);
            String targetLevel = (String) requestBody.get("targetLevel");
            if (targetLevel != null && !targetLevel.isBlank()) settings.setTargetLevel(targetLevel);
            Object weeks = requestBody.get("weeks");
            if (weeks instanceof Number n) settings.setPrepWeeks(n.intValue());
            Object hours = requestBody.get("hoursPerWeek");
            if (hours instanceof Number n) settings.setHoursPerWeek(n.intValue());
        }
        userSettingsRepository.save(settings);

        ResumeProfile profile = requireProfile(userId);
        if (profile.getConfirmedJson() == null) {
            throw new ApiException(ErrorCode.PLAN_NOT_COMMITTED, HttpStatus.CONFLICT);
        }
        Map<String, Object> profileMap = objectMapper.readValue(
                profile.getConfirmedJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        if (profileMap == null || profileMap.isEmpty()) {
            // confirmedJson can be the literal string "null" if confirm was called with no profile —
            // generating from it would silently produce a generic plan that ignores the resume
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Confirmed profile is empty. Re-confirm your profile before generating a plan.");
        }

        // Build structured targets map so Python/plan.md receives real values
        java.util.HashMap<String, Object> targetsMap = new java.util.HashMap<>();
        if (settings.getTargetRole()  != null) targetsMap.put("targetRole",   settings.getTargetRole());
        if (settings.getTargetLevel() != null) targetsMap.put("targetLevel",  settings.getTargetLevel());
        if (settings.getPrepWeeks()   != null) targetsMap.put("prepWeeks",    settings.getPrepWeeks());
        if (settings.getHoursPerWeek()!= null) targetsMap.put("hoursPerWeek", settings.getHoursPerWeek());
        if (requestBody != null) {
            Object interests = requestBody.get("interests");
            if (interests != null) targetsMap.put("interests", interests);
        }

        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("profile", profileMap);
        body.put("targets", targetsMap);
        String additionalContext = requestBody != null ? (String) requestBody.get("additionalContext") : null;
        if (additionalContext != null && !additionalContext.isBlank()) {
            body.put("additionalContext", additionalContext.strip());
        }
        if (requestBody != null && Boolean.TRUE.equals(requestBody.get("regenerate"))) {
            body.put("regenerate", true);
        }

        Map<String, Object> response = aiClient.post(userId, "/ai/generate-plan", body, "plan-generate");
        Object result = response.get("result");
        if (result instanceof Map<?,?> resultMap && resultMap.containsKey("phases")) {
            return Map.of("phases", resultMap.get("phases"));
        }
        return response;
    }

    @Transactional
    public Map<String, Object> submitPlanManual(Long userId, Map<String, Object> body) {
        Object phases = body.get("phases");
        if (phases == null) {
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return Map.of("phases", phases);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> commitPlan(Long userId, Map<String, Object> body) {
        // 1. Mark profile + user as onboarded
        ResumeProfile profile = requireProfile(userId);
        profile.setPlanCommitted(true);
        profile.setUpdatedAt(Instant.now());
        resumeProfileRepository.save(profile);

        UserSettings settings = userSettingsRepository.findByUserId(userId).orElseGet(UserSettings::new);
        settings.setUserId(userId);
        settings.setOnboarded(true);
        userSettingsRepository.save(settings);

        // 2. Persist Phase → Week → Topic records (replace any existing plan)
        List<Map<String, Object>> phasesData =
                (List<Map<String, Object>>) body.get("phases");
        if (phasesData != null && !phasesData.isEmpty()) {
            // Delete existing plan for this user via JPQL (DB cascade handles weeks+topics)
            em.createQuery("DELETE FROM Phase p WHERE p.userId = :uid").setParameter("uid", userId).executeUpdate();
            em.flush();

            int phaseOrd = 0;
            for (Map<String, Object> pd : phasesData) {
                Phase phase = new Phase();
                phase.setUserId(userId);
                phase.setName(str(pd, "name", "Phase " + (phaseOrd + 1)));
                phase.setCode("phase-" + (phaseOrd + 1));
                phase.setBlurb(str(pd, "goal", str(pd, "blurb", "")));
                phase.setDisplayOrder(phaseOrd++);
                phase = phaseRepository.save(phase);

                List<Map<String, Object>> weeksData = weeksDetail(pd);
                int weekOrd = 0;
                for (Map<String, Object> wd : weeksData) {
                    int weekNum = num(wd, "week_number", weekOrd + 1);
                    Week week = new Week();
                    week.setUserId(userId);
                    week.setPhase(phase);
                    week.setCode("w-" + phaseOrd + "-" + weekNum);
                    week.setTitle("Week " + weekNum);
                    week.setDisplayOrder(weekOrd++);
                    week = weekRepository.save(week);

                    List<Map<String, Object>> topicsData =
                            (List<Map<String, Object>>) wd.getOrDefault("topics", List.of());
                    int topicOrd = 0;
                    for (Map<String, Object> td : topicsData) {
                        String title = str(td, "title", "Topic");
                        if (title.length() > 255) title = title.substring(0, 255);
                        Topic topic = new Topic();
                        topic.setUserId(userId);
                        topic.setWeek(week);
                        topic.setTitle(title);
                        topic.setSlug(uniqueSlug(userId, title));
                        topic.setCode("t-" + System.nanoTime());
                        topic.setSource("ai-generated");
                        topic.setStatus("todo");
                        topic.setTag("new");
                        String category = str(td, "category", null);
                        if (category != null && !category.isBlank()) topic.setCategory(category);
                        topic.setDisplayOrder(topicOrd++);
                        String hint = str(td, "resources_hint", str(td, "angle", null));
                        if (hint != null) topic.setAngle(hint);
                        topicRepository.save(topic);

                        persistTopicContent(userId, topic.getId(), td);
                    }
                }
            }
        }

        return Map.of("status", "committed");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void persistTopicContent(Long userId, Long topicId, Map<String, Object> td) {
        if (td.get("resources") instanceof List<?> rawRes) {
            int ord = 0;
            for (Object r : rawRes) {
                if (!(r instanceof Map<?, ?> rm)) continue;
                String label = rm.get("label") instanceof String s ? s : null;
                String url   = rm.get("url")   instanceof String s ? s : null;
                if (label != null && !label.isBlank() && url != null && !url.isBlank()) {
                    Resource res = new Resource();
                    res.setUserId(userId);
                    res.setTopicId(topicId);
                    res.setLabel(label);
                    res.setUrl(url);
                    res.setSource("ai");
                    res.setDisplayOrder(ord++);
                    resourceRepository.save(res);
                }
            }
        }

        if (td.get("questions") instanceof List<?> rawQ) {
            int ord = 0;
            for (Object q : rawQ) {
                String text = q instanceof String s ? s
                        : q instanceof Map<?, ?> m && m.get("text") instanceof String s ? s : null;
                if (text != null && !text.isBlank()) {
                    Question question = new Question();
                    question.setUserId(userId);
                    question.setTopicId(topicId);
                    question.setText(text);
                    question.setSource("ai");
                    question.setDisplayOrder(ord++);
                    questionRepository.save(question);
                }
            }
        }

        if (td.get("exercises") instanceof List<?> rawEx) {
            int ord = 0;
            for (Object e : rawEx) {
                if (!(e instanceof Map<?, ?> em)) continue;
                String title = em.get("title") instanceof String s ? s : null;
                if (title == null || title.isBlank()) continue;
                String url = em.get("url") instanceof String s && !s.isBlank() ? s
                           : em.get("repoUrl") instanceof String s && !s.isBlank() ? s : null;
                Exercise exercise = new Exercise();
                exercise.setUserId(userId);
                exercise.setTopicId(topicId);
                exercise.setTitle(title);
                exercise.setRepoUrl(url);
                exercise.setDone(false);
                exercise.setSource("ai");
                exercise.setDisplayOrder(ord++);
                exerciseRepository.save(exercise);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> weeksDetail(Map<String, Object> phase) {
        // AI JSON uses "weeks_detail"; frontend Phase type uses "weeks" (same array)
        Object wd = phase.getOrDefault("weeks_detail", phase.get("weeks"));
        if (wd instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?,?>) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private static int num(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private String toSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }

    private String uniqueSlug(Long userId, String title) {
        String base = toSlug(title);
        if (base.isBlank()) base = "topic";
        // leave room for the "-N" uniqueness suffix within the 255-char column
        if (base.length() > 240) base = base.substring(0, 240);
        String slug = base;
        int i = 2;
        while (topicRepository.existsByUserIdAndSlug(userId, slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    private String extractText(MultipartFile file) throws IOException {
        String ct = file.getContentType();
        if ("application/pdf".equalsIgnoreCase(ct)
                || (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf"))) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                String text = new PDFTextStripper().getText(doc).strip();
                if (!text.isBlank()) return text;
            }
            return "[PDF uploaded but no extractable text layer — likely a scanned document]";
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private ResumeProfile requireProfile(Long userId) {
        return resumeProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
    }
}

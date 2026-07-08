package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.content.ContentService;
import com.interview.prep.platform.backend_core.content.dto.ExerciseDto;
import com.interview.prep.platform.backend_core.content.dto.QuestionDto;
import com.interview.prep.platform.backend_core.content.dto.ResourceDto;
import com.interview.prep.platform.backend_core.study.Phase;
import com.interview.prep.platform.backend_core.study.PhaseRepository;
import com.interview.prep.platform.backend_core.study.StudyService;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.study.Week;
import org.springframework.transaction.annotation.Transactional;
import com.interview.prep.platform.backend_core.study.dto.UpdateTopicDto;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfile;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfileRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGatewayService {

    private final AiClient aiClient;
    private final TopicRepository topicRepository;
    private final ContentService contentService;
    private final StudyService studyService;
    private final UserSettingsRepository userSettingsRepository;
    private final ResumeProfileRepository resumeProfileRepository;
    private final PhaseRepository phaseRepository;

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateForTab(Long userId, String slug, String tab) {
        Topic topic = topicRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Topic not found: " + slug));

        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        String seniority = (settings != null && settings.getTargetLevel() != null)
                ? settings.getTargetLevel() : "mid";

        Map<String, Object> payload = new HashMap<>();
        payload.put("tab", tab);
        payload.put("topic_title", topic.getTitle());
        payload.put("topic_category", topic.getCategory() != null ? topic.getCategory() : "");
        payload.put("seniority", seniority);
        payload.put("confidence", topic.getConfidence() != null ? topic.getConfidence() : 3);
        payload.put("skills", skillSummaries(loadProfile(userId)));
        payload.put("angle", topic.getAngle() != null ? topic.getAngle() : "");

        Map<String, Object> response = aiClient.post(userId, "/ai/guide", payload, "guide-" + tab);
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            saveTabResult(userId, slug, tab, (Map<String, Object>) resultMap);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("status", "ok");
        out.put("budgetWarning", response.getOrDefault("budgetWarning", false));
        return out;
    }

    public Map<String, Object> seedPlan(Long userId) {
        return seedPlan(userId, null);
    }

    /** providerOverride "desktop" returns the seed prompt for copy-paste instead of calling a model. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> seedPlan(Long userId, String providerOverride) {
        List<Topic> topics = topicRepository.findByUserIdOrdered(userId);
        return seedSupport(userId, topics, providerOverride);
    }

    /** Support pass for a subset (pipeline runs this per week, after depth). */
    public Map<String, Object> seedSupportForTopics(Long userId, List<Topic> topics,
                                                    Integer batchSize, String modelOverride) {
        return seedSupport(userId, topics, null, batchSize, modelOverride);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> seedSupport(Long userId, List<Topic> topics, String providerOverride) {
        return seedSupport(userId, topics, providerOverride, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> seedSupport(Long userId, List<Topic> topics, String providerOverride,
                                            Integer batchSize, String modelOverride) {
        if (topics.isEmpty()) return Map.of("seeded", 0, "total", 0);

        List<Map<String, String>> topicList = topics.stream()
                .map(t -> Map.of("slug", t.getSlug(), "title", t.getTitle(),
                        "category", t.getCategory() != null ? t.getCategory() : "",
                        "hint", t.getAngle() != null ? t.getAngle() : ""))
                .toList();

        // Include parsed resume profile and target role so the seed can tailor resources
        Map<String, Object> profile = loadProfile(userId);
        String targetRole = "";
        try {
            UserSettings us = userSettingsRepository.findByUserId(userId).orElse(null);
            if (us != null) {
                targetRole = (us.getTargetRole() != null ? us.getTargetRole() : "")
                        + (us.getTargetLevel() != null ? " " + us.getTargetLevel() : "");
            }
        } catch (Exception e) {
            log.warn("Could not load settings for seed: {}", e.getMessage());
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("topics", topicList);
        requestBody.put("profile", profile);
        requestBody.put("target_role", targetRole.strip());
        if (batchSize != null) requestBody.put("batch_size", batchSize);

        Map<String, Object> response = aiClient.post(userId, "/ai/seed-plan", requestBody, "seed-plan",
                providerOverride, null, modelOverride);

        Object result = response.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) return Map.of("seeded", 0, "total", topics.size());

        List<Map<String, Object>> topicsResult = (List<Map<String, Object>>) resultMap.get("topics");
        if (topicsResult == null) return Map.of("seeded", 0, "total", topics.size());

        int seeded = persistSeedTopics(userId, topicsResult);
        return Map.of("seeded", seeded, "total", topics.size());
    }

    /** Desktop mode: the user pasted the seed JSON generated by their own AI. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> seedPlanManual(Long userId, Map<String, Object> body) {
        if (!(body.get("topics") instanceof List<?> raw)) {
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Expected JSON with a top-level \"topics\" array");
        }
        int seeded = persistSeedTopics(userId, (List<Map<String, Object>>) raw);
        return Map.of("seeded", seeded, "total", raw.size());
    }

    private int persistSeedTopics(Long userId, List<Map<String, Object>> topicsResult) {
        int seeded = 0;
        for (Map<String, Object> topicData : topicsResult) {
            String slug = str(topicData, "slug");
            if (slug.isBlank()) continue;
            try {
                if (topicData.get("concept") instanceof String c && !c.isBlank()) {
                    saveTabResult(userId, slug, "overview", topicData);
                }
                saveTabResult(userId, slug, "resources", topicData);
                saveTabResult(userId, slug, "exercises", topicData);
                saveTabResult(userId, slug, "questions", topicData);
                seeded++;
            } catch (Exception e) {
                log.warn("Seed failed for topic {}: {}", slug, e.getMessage());
            }
        }
        return seeded;
    }

    @SuppressWarnings("unchecked")
    private void saveTabResult(Long userId, String slug, String tab, Map<String, Object> result) {
        switch (tab) {
            case "overview" -> {
                String concept = strOrNull(result, "concept");
                List<String> points = result.get("points") instanceof List<?> l
                        ? l.stream().map(Object::toString).toList() : List.of();
                String angle = strOrNull(result, "angle");
                studyService.updateTopic(userId, slug,
                        new UpdateTopicDto(null, null, null, concept, points.isEmpty() ? null : points, angle, null));
            }
            case "resources" -> {
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("resources");
                if (items == null) return;
                List<ResourceDto> dtos = items.stream()
                        .map(r -> new ResourceDto(null, str(r, "label"), str(r, "url"), 0))
                        .filter(d -> !d.label().isBlank() && !d.url().isBlank())
                        .toList();
                if (!dtos.isEmpty()) contentService.replaceAiResources(userId, slug, dtos);
            }
            case "exercises" -> {
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("exercises");
                if (items == null) return;
                List<ExerciseDto> dtos = items.stream()
                        .map(e -> new ExerciseDto(null, str(e, "title"), str(e, "repoUrl"), false, 0,
                                e.get("est_minutes") instanceof Number n ? n.intValue() : null))
                        .filter(d -> !d.title().isBlank())
                        .toList();
                if (!dtos.isEmpty()) contentService.replaceAiExercises(userId, slug, dtos);
            }
            case "questions" -> {
                List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("questions");
                if (items == null) return;
                List<QuestionDto> dtos = items.stream()
                        .map(q -> new QuestionDto(null, str(q, "text"), 0, str(q, "type")))
                        .filter(d -> !d.text().isBlank())
                        .toList();
                if (!dtos.isEmpty()) contentService.replaceAiQuestions(userId, slug, dtos);
            }
        }
    }

    /** ["Java (expert)", ...] for callers outside this service (e.g. mock interviews). */
    public List<String> profileSkills(Long userId) {
        return skillSummaries(loadProfile(userId));
    }

    /**
     * Narrative pass (pipeline v2 §2): one cheap call over the frozen structure —
     * phase blurbs, week bridges/anchors — persisted onto phase/week rows.
     */
    @Transactional
    public void generateNarrative(Long userId) {
        generateNarrative(userId, null);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void generateNarrative(Long userId, String modelOverride) {
        List<Phase> phases = phaseRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        if (phases.isEmpty()) return;

        // flatten weeks with global numbering, preserving phase order
        List<Week> orderedWeeks = new ArrayList<>();
        List<Map<String, Object>> weekPayload = new ArrayList<>();
        List<Map<String, Object>> phasePayload = new ArrayList<>();
        int weekNo = 0;
        for (Phase p : phases) {
            phasePayload.add(Map.of("name", p.getName(), "goal", p.getBlurb() != null ? p.getBlurb() : ""));
            for (Week w : p.getWeeks()) {
                weekNo++;
                orderedWeeks.add(w);
                weekPayload.add(Map.of("week_number", weekNo, "title", w.getTitle(), "phase", p.getName()));
            }
        }

        List<String> highlights = new ArrayList<>();
        if (loadProfile(userId).get("experiences") instanceof List<?> exps) {
            for (Object e : exps) {
                if (e instanceof Map<?, ?> m && m.get("highlights") instanceof List<?> hs) {
                    hs.forEach(h -> highlights.add(String.valueOf(h)));
                }
            }
        }

        UserSettings us = userSettingsRepository.findByUserId(userId).orElse(null);
        Map<String, Object> body = new HashMap<>();
        body.put("phases", phasePayload);
        body.put("weeks", weekPayload);
        body.put("highlights", highlights);
        body.put("target_role", us != null && us.getTargetRole() != null ? us.getTargetRole() : "Software Engineer");

        Map<String, Object> response = aiClient.post(userId, "/ai/narrative", body, "narrative",
                null, null, modelOverride);
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)) return;

        if (result.get("phases") instanceof List<?> blurbs) {
            for (int i = 0; i < phases.size() && i < blurbs.size(); i++) {
                if (blurbs.get(i) instanceof Map<?, ?> m && m.get("blurb") instanceof String b && !b.isBlank()) {
                    phases.get(i).setBlurb(b);
                }
            }
            phaseRepository.saveAll(phases);
        }

        if (result.get("weeks") instanceof List<?> weeks) {
            for (Object o : weeks) {
                if (!(o instanceof Map<?, ?> wm) || !(wm.get("week_number") instanceof Number n)) continue;
                int idx = n.intValue() - 1;
                if (idx < 0 || idx >= orderedWeeks.size()) continue;
                Week week = orderedWeeks.get(idx);
                if (wm.get("bridge") instanceof String bridge && !bridge.isBlank()) week.setBridge(bridge);
                if (wm.get("unlocks") instanceof String unlocks && !unlocks.isBlank()) week.setUnlocks(unlocks);
                if (wm.get("anchor") instanceof String anchor && !anchor.isBlank()) week.setAnchor(anchor);
                if (wm.get("builds_on") instanceof List<?> buildsOn) {
                    // keep only references to earlier weeks
                    List<Map<String, Object>> valid = new ArrayList<>();
                    for (Object bo : buildsOn) {
                        if (bo instanceof Map<?, ?> bm && bm.get("week") instanceof Number wn
                                && wn.intValue() >= 1 && wn.intValue() < n.intValue()) {
                            Object why = bm.get("why");
                            valid.add(Map.of("week", wn.intValue(),
                                    "why", why != null ? String.valueOf(why) : ""));
                        }
                    }
                    try {
                        week.setBuildsOn(new ObjectMapper().writeValueAsString(valid));
                    } catch (Exception ignored) { /* narrative extras are best-effort */ }
                }
            }
        }
    }

    /** Load the parsed resume profile, unwrapping the {"result": {...}} envelope if present. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadProfile(Long userId) {
        try {
            ResumeProfile rp = resumeProfileRepository.findByUserId(userId).orElse(null);
            if (rp == null || rp.getParsedJson() == null) return Map.of();
            Map<String, Object> profile = new ObjectMapper().readValue(rp.getParsedJson(), Map.class);
            if (profile.get("result") instanceof Map<?, ?> inner) {
                return (Map<String, Object>) inner;
            }
            return profile;
        } catch (Exception e) {
            log.warn("Could not load resume profile: {}", e.getMessage());
            return Map.of();
        }
    }

    /** ["Java (expert)", "Kubernetes (beginner)", ...] from the parsed profile. */
    private static List<String> skillSummaries(Map<String, Object> profile) {
        if (!(profile.get("skills") instanceof List<?> raw)) return List.of();
        return raw.stream()
                .filter(s -> s instanceof Map<?, ?> m && m.get("name") instanceof String)
                .map(s -> {
                    Map<?, ?> m = (Map<?, ?>) s;
                    String name = (String) m.get("name");
                    return m.get("level") instanceof String level ? name + " (" + level + ")" : name;
                })
                .toList();
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : "";
    }

    private static String strOrNull(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof String s && !s.isBlank() ? s : null;
    }
}

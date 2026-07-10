package com.interview.prep.platform.backend_core.ai;

import com.interview.prep.platform.backend_core.IntegrationTestBase;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfile;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfileRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/plan/audit — checklist coverage + week feasibility over a draft plan. No model call. */
class PlanAuditIT extends IntegrationTestBase {

    @Autowired ResumeProfileRepository resumeProfileRepository;

    @BeforeEach
    void setUp() {
        baseSetUp();
        resumeProfileRepository.deleteAll();

        ResumeProfile profile = new ResumeProfile();
        profile.setUserId(testUserId);
        profile.setConfirmedJson("""
                {"skills":[{"name":"Java","level":"expert","years":11},
                           {"name":"Kubernetes","level":"intermediate","years":3}]}""");
        resumeProfileRepository.save(profile);

        UserSettings settings = settingsRepository.findByUserId(testUserId).orElseThrow();
        settings.setHoursPerWeek(10);
        settingsRepository.save(settings);
    }

    private String draftPlan(List<String> topicTitles) throws Exception {
        return objectMapper.writeValueAsString(Map.of("phases", List.of(Map.of(
                "name", "Technical Skills Depth Review",
                "weeks", List.of(Map.of(
                        "title", "Week 1 — JVM internals",
                        "topics", topicTitles.stream()
                                .map(t -> Map.of("title", t, "category", "language", "scope", t))
                                .toList()))))));
    }

    @Test
    @DisplayName("a thin java plan reports low coverage and suggests the gaps into a matching week")
    void reportsGapsForClaimedExpertSkill() throws Exception {
        String body = draftPlan(List.of(
                "Heap layout and the generational hypothesis",
                "ThreadPoolExecutor internals — sizing and rejection"));

        mockMvc.perform(post("/api/plan/audit")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // Kubernetes is only "intermediate" — no checklist is applied to it
                .andExpect(jsonPath("$.coverage.length()").value(1))
                .andExpect(jsonPath("$.coverage[0].skill").value("java"))
                .andExpect(jsonPath("$.coverage[0].ready").value(false))
                .andExpect(jsonPath("$.suggestions[?(@.id == 'virtual-threads')]").exists())
                .andExpect(jsonPath("$.suggestions[?(@.id == 'generics')].weekIndex").value(0))
                .andExpect(jsonPath("$.budgetMinutes").value(600));
    }

    @Test
    @DisplayName("an over-stuffed week is flagged against the weekly hour budget")
    void flagsOverloadedWeek() throws Exception {
        // 15 coarse topics × 45 assumed minutes = 675 > 600
        List<String> many = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> "Topic " + i + " — mechanism").toList();

        mockMvc.perform(post("/api/plan/audit")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPlan(many)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeks[0].over").value(true))
                .andExpect(jsonPath("$.weeks[0].estimated").value(true))
                .andExpect(jsonPath("$.weeks[0].plannedMinutes").value(675));
    }

    @Test
    @DisplayName("no résumé skills claimed deeply → nothing to audit, and no crash")
    void emptyProfileAuditsCleanly() throws Exception {
        resumeProfileRepository.deleteAll();

        mockMvc.perform(post("/api/plan/audit")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftPlan(List.of("Heap layout"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverage.length()").value(0))
                .andExpect(jsonPath("$.suggestions.length()").value(0));
    }
}

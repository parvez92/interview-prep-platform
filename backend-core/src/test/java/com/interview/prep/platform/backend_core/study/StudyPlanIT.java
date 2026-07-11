package com.interview.prep.platform.backend_core.study;

import com.interview.prep.platform.backend_core.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 2 — Study plan CRUD + progress.
 *
 * Tests the REST API end-to-end against a live Postgres:
 *  GET  /api/phases            → phase tree with topics
 *  GET  /api/topics/{slug}     → topic workspace payload
 *  PATCH /api/topics/{slug}    → mark done / set confidence
 *  GET  /api/progress          → overall % + per-track readiness
 *
 * No AI calls in this slice.
 */
class StudyPlanIT extends IntegrationTestBase {

    @Autowired private PhaseRepository  phaseRepository;
    @Autowired private WeekRepository   weekRepository;
    @Autowired private TopicRepository  topicRepository;

    private Phase testPhase;
    private Topic testTopic;

    @BeforeEach
    void setUp() {
        baseSetUp();
        seedStudyData();
    }

    @Transactional
    void seedStudyData() {
        topicRepository.deleteAll();
        weekRepository.deleteAll();
        phaseRepository.deleteAll();

        testPhase = new Phase();
        testPhase.setUserId(testUserId);
        testPhase.setCode("DSA");
        testPhase.setName("Data Structures");
        testPhase.setIcon("🧩");
        testPhase.setBlurb("Core DS topics");
        testPhase.setDisplayOrder(1);
        phaseRepository.save(testPhase);

        Week week = new Week();
        week.setUserId(testUserId);
        week.setPhase(testPhase);
        week.setCode("W1");
        week.setTitle("Week 1 — Arrays & Hashing");
        week.setDisplayOrder(1);
        weekRepository.save(week);

        testTopic = new Topic();
        testTopic.setUserId(testUserId);
        testTopic.setWeek(week);
        testTopic.setCode("ARRAYS");
        testTopic.setSlug("arrays");
        testTopic.setTitle("Arrays & Two-Pointer");
        testTopic.setTag("dsa");
        testTopic.setSource("standard");
        testTopic.setStatus("todo");
        testTopic.setConfidence(70);
        testTopic.setConcept("Contiguous memory blocks enabling O(1) index access.");
        testTopic.setPoints("[\"O(1) access\",\"O(n) search\",\"Two-pointer technique\"]");
        testTopic.setAngle("When to prefer array vs linked list");
        testTopic.setDisplayOrder(1);
        topicRepository.save(testTopic);
    }

    // ── GET /api/phases ─────────────────────────────────────────────────────

    @Test
    void getPhases_returnsPhaseTreeWithTopics() throws Exception {
        mockMvc.perform(get("/api/phases").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("DSA"))
                .andExpect(jsonPath("$[0].name").value("Data Structures"))
                .andExpect(jsonPath("$[0].weeks", hasSize(1)))
                .andExpect(jsonPath("$[0].weeks[0].topics", hasSize(1)))
                .andExpect(jsonPath("$[0].weeks[0].topics[0].slug").value("arrays"))
                .andExpect(jsonPath("$[0].weeks[0].topics[0].status").value("todo"))
                .andExpect(jsonPath("$[0].progress.done").value(0))
                .andExpect(jsonPath("$[0].progress.total").value(1));
    }

    @Test
    void getPhases_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/phases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPhases_doesNotReturnOtherUserTopics() throws Exception {
        // Seed a second user with their own phase — must not appear in test user's response
        var other = new com.interview.prep.platform.backend_core.user.AppUser();
        other.setEmail("other@test.com");
        other.setPasswordHash("x");
        other.setDisplayName("Other");
        var savedOther = userRepository.save(other);

        var otherPhase = new Phase();
        otherPhase.setUserId(savedOther.getId());
        otherPhase.setCode("BEHAV"); otherPhase.setName("Behavioral");
        otherPhase.setIcon("💬"); otherPhase.setBlurb(""); otherPhase.setDisplayOrder(1);
        phaseRepository.save(otherPhase);

        mockMvc.perform(get("/api/phases").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))          // only test user's phase
                .andExpect(jsonPath("$[0].code").value("DSA"));
    }

    // ── GET /api/topics/{slug} ───────────────────────────────────────────────

    @Test
    void getTopicDetail_returnsWorkspacePayload() throws Exception {
        mockMvc.perform(get("/api/topics/arrays").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("arrays"))
                .andExpect(jsonPath("$.title").value("Arrays & Two-Pointer"))
                .andExpect(jsonPath("$.deepDive.concept").value(containsString("O(1) index access")))
                .andExpect(jsonPath("$.deepDive.angle").exists())
                .andExpect(jsonPath("$.note").exists())
                .andExpect(jsonPath("$.resources").isArray())
                .andExpect(jsonPath("$.exercises").isArray())
                .andExpect(jsonPath("$.questions").isArray());
    }

    @Test
    void getTopicDetail_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/api/topics/nonexistent").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTopicDetail_anotherUserSlug_returns404() throws Exception {
        var other = new com.interview.prep.platform.backend_core.user.AppUser();
        other.setEmail("other2@test.com"); other.setPasswordHash("x"); other.setDisplayName("O2");
        var savedOther = userRepository.save(other);

        // Create topic owned by other user with same slug "arrays"
        Phase op = new Phase(); op.setUserId(savedOther.getId()); op.setCode("X");
        op.setName("X"); op.setIcon("X"); op.setBlurb(""); op.setDisplayOrder(1);
        phaseRepository.save(op);
        Week ow = new Week(); ow.setUserId(savedOther.getId()); ow.setPhase(op);
        ow.setCode("W1"); ow.setTitle("W"); ow.setDisplayOrder(1);
        weekRepository.save(ow);
        Topic ot = new Topic(); ot.setUserId(savedOther.getId()); ot.setWeek(ow);
        ot.setSlug("arrays-other"); ot.setCode("X"); ot.setTitle("X");
        ot.setTag("new"); ot.setSource("standard"); ot.setStatus("todo");
        ot.setPoints("[]"); ot.setDisplayOrder(1);
        topicRepository.save(ot);

        // Test user trying to access other user's topic slug → 404
        mockMvc.perform(get("/api/topics/arrays-other").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/topics/{slug} ─────────────────────────────────────────────

    @Test
    void patchTopic_markDone_updatesStatusAndProgress() throws Exception {
        mockMvc.perform(patch("/api/topics/arrays")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "done"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        // Progress should now show 1 done
        mockMvc.perform(get("/api/phases").header("Authorization", bearer()))
                .andExpect(jsonPath("$[0].progress.done").value(1))
                .andExpect(jsonPath("$[0].progress.total").value(1));
    }

    @Test
    void patchTopic_optimisticToggle_canUndone() throws Exception {
        // Mark done, then undo
        String auth = bearer();
        mockMvc.perform(patch("/api/topics/arrays").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "done"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/topics/arrays").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "todo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("todo"));
    }

    @Test
    void patchTopic_setConfidence_persists() throws Exception {
        mockMvc.perform(patch("/api/topics/arrays")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("confidence", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(4));
    }

    // ── priority (plan amendment 01 §1) ──────────────────────────────────────

    @Test
    void getPhases_topicCarriesDefaultHighPriority() throws Exception {
        mockMvc.perform(get("/api/phases").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weeks[0].topics[0].priority").value("high"));
    }

    @Test
    void patchTopic_setPriority_persists() throws Exception {
        mockMvc.perform(patch("/api/topics/arrays")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("priority", "low"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("low"));
    }

    @Test
    void createTopic_byWeekNumber_withSourceAndPriority() throws Exception {
        // the seeded week is "W1" (no global number); give the topic a resolvable coded week
        var week = new Week();
        week.setUserId(testUserId);
        week.setPhase(testPhase);
        week.setCode("w-1-14");
        week.setTitle("Week 14 — DSA");
        week.setDisplayOrder(2);
        weekRepository.save(week);

        String body = objectMapper.writeValueAsString(Map.of(
                "weekNumber", 14,
                "title", "Low-level design & design patterns",
                "tag", "new",
                "source", "standard",
                "priority", "high",
                "scope", "SOLID live, GoF patterns, machine-coding staples"));

        mockMvc.perform(post("/api/topics")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("standard"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.isCustom").value(false))
                .andExpect(jsonPath("$.deepDive.angle").value(org.hamcrest.Matchers.containsString("SOLID")));
    }

    @Test
    void createTopic_longTitle_doesNotOverflowCodeColumn() throws Exception {
        // code is varchar(40); a long amendment title must not be used to derive it
        String longTitle = "Low-level design & design patterns — SOLID live, machine-coding round";
        mockMvc.perform(post("/api/topics")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "weekCode", "W1", "title", longTitle, "tag", "new"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(longTitle));
    }

    @Test
    void createTopic_bareUserAdd_defaultsCustomHigh() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "weekCode", "W1",
                "title", "My own scratch topic",
                "tag", "new"));

        mockMvc.perform(post("/api/topics")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("custom"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.isCustom").value(true));
    }

    // ── GET /api/progress ───────────────────────────────────────────────────

    @Test
    void progress_freshPlan_returnsZeroPercent() throws Exception {
        mockMvc.perform(get("/api/progress").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallPct").isNumber())
                .andExpect(jsonPath("$.streak").isNumber())
                .andExpect(jsonPath("$.tracks").isArray())
                .andExpect(jsonPath("$.flaggedCount").value(0));
    }

    @Test
    void progress_afterMarkingDone_overallPctIncreases() throws Exception {
        String auth = bearer();
        double before = overallPct(auth);

        mockMvc.perform(patch("/api/topics/arrays").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "done"))))
                .andExpect(status().isOk());

        double after = overallPct(auth);
        org.assertj.core.api.Assertions.assertThat(after).isGreaterThan(before);
    }

    private double overallPct(String auth) throws Exception {
        String body = mockMvc.perform(get("/api/progress").header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("overallPct").asDouble();
    }
}

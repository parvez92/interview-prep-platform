package com.interview.prep.platform.backend_core.interview;

import com.interview.prep.platform.backend_core.IntegrationTestBase;
import com.interview.prep.platform.backend_core.study.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice 3 — The feedback loop: the core product differentiator.
 *
 * Full HTTP path tested against live Postgres:
 *
 *  POST /api/interviews              → create interview
 *  POST /api/interviews/{id}/questions  (selfRating ≤ 2)
 *       → FeedbackLoopService creates review_flag
 *       → topic.confidence lowered
 *       → response includes weak[] with topic slug
 *  GET  /api/review/today            → flagged topic appears
 *  PATCH /api/interviews/questions/{id}  (selfRating raised to 4)
 *       → review_flag resolved
 *       → topic no longer in review/today
 *  GET  /api/progress                → flaggedCount reflects live state
 */
class InterviewFeedbackLoopIT extends IntegrationTestBase {

    @Autowired private PhaseRepository         phaseRepository;
    @Autowired private WeekRepository          weekRepository;
    @Autowired private TopicRepository         topicRepository;
    @Autowired private InterviewRepository     interviewRepository;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private ReviewFlagRepository    flagRepository;

    private Topic testTopic;

    @BeforeEach
    void setUp() {
        baseSetUp();
        seedStudyPlan();
    }

    @Transactional
    void seedStudyPlan() {
        flagRepository.deleteAll();
        questionRepository.deleteAll();
        interviewRepository.deleteAll();
        topicRepository.deleteAll();
        weekRepository.deleteAll();
        phaseRepository.deleteAll();

        Phase phase = new Phase();
        phase.setUserId(testUserId); phase.setCode("DSA");
        phase.setName("DSA"); phase.setIcon("🔢"); phase.setBlurb(""); phase.setDisplayOrder(1);
        phaseRepository.save(phase);

        Week week = new Week();
        week.setUserId(testUserId); week.setPhase(phase);
        week.setCode("W1"); week.setTitle("Week 1"); week.setDisplayOrder(1);
        weekRepository.save(week);

        testTopic = new Topic();
        testTopic.setUserId(testUserId); testTopic.setWeek(week);
        testTopic.setCode("ARRAYS"); testTopic.setSlug("arrays");
        testTopic.setTitle("Arrays"); testTopic.setTag("dsa"); testTopic.setSource("standard");
        testTopic.setStatus("todo"); testTopic.setConfidence(80);
        testTopic.setPoints("[]"); testTopic.setDisplayOrder(1);
        topicRepository.save(testTopic);
    }

    // ── Create interview ─────────────────────────────────────────────────────

    @Test
    void createInterview_returns201WithId() throws Exception {
        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "company", "Google",
                                "role",    "L5 SWE",
                                "stage",   "onsite",
                                "round",   "Technical"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.company").value("Google"));
    }

    // ── Feedback loop: low rating → flag ─────────────────────────────────────

    @Test
    void logQuestion_selfRatingLeq2_createsFlagAndLowersConfidence() throws Exception {
        String auth  = bearer();
        long intId   = createInterview(auth, "Meta");
        int  before  = getConfidence(testTopic.getId());

        // Log a question with selfRating = 1 (≤ 2 threshold)
        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text",       "What is amortised complexity of ArrayList add?",
                                "topicSlug",  "arrays",
                                "selfRating", 1
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weak", hasItem("arrays")));

        // Flag must be created
        List<ReviewFlag> flags = flagRepository.findByUserIdAndTopicIdAndResolved(testUserId, testTopic.getId(), false);
        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getSource()).isEqualTo("interview");
        assertThat(flags.get(0).getReason()).contains("1/5").contains("Meta");

        // Confidence must have dropped by 30
        int after = getConfidence(testTopic.getId());
        assertThat(after).isEqualTo(before - 30);
    }

    @Test
    void logQuestion_selfRatingAtThreshold_alsoCreatesFlag() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Apple");

        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 2
                        ))))
                .andExpect(status().isCreated());

        assertThat(flagRepository.findByUserIdAndTopicIdAndResolved(testUserId, testTopic.getId(), false))
                .hasSize(1);
    }

    @Test
    void logQuestion_highRating_doesNotCreateFlag() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Amazon");

        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 4
                        ))))
                .andExpect(status().isCreated());

        assertThat(flagRepository.findByUserIdAndTopicIdAndResolved(testUserId, testTopic.getId(), false))
                .isEmpty();
    }

    // ── Flagged topic appears in review/today ─────────────────────────────────

    @Test
    void reviewToday_afterLowRating_includesFlaggedTopic() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Netflix");

        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 1
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/review/today").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind=='flagged' && @.topicSlug=='arrays')]").exists());
    }

    // ── Rating raised → flag resolved ────────────────────────────────────────

    @Test
    void updateQuestion_raiseRating_resolvesFlagAndRemovesFromReview() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Stripe");

        // Log with low rating
        String createBody = mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 1
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long questionId = objectMapper.readTree(createBody)
                .path("questions").elements().next().path("id").asLong();

        // Flag must exist before update
        assertThat(flagRepository.findByUserIdAndTopicIdAndResolved(testUserId, testTopic.getId(), false)).hasSize(1);

        // Raise rating
        mockMvc.perform(patch("/api/interviews/questions/" + questionId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 4
                        ))))
                .andExpect(status().isOk());

        // Flag must now be resolved
        assertThat(flagRepository.findByUserIdAndTopicIdAndResolved(testUserId, testTopic.getId(), false)).isEmpty();

        // review/today must no longer have the flagged item
        mockMvc.perform(get("/api/review/today").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind=='flagged' && @.topicSlug=='arrays')]").doesNotExist());
    }

    // ── progress.flaggedCount ────────────────────────────────────────────────

    @Test
    void progress_reflectsFlaggedCount() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Uber");

        // Before: 0 flags
        mockMvc.perform(get("/api/progress").header("Authorization", auth))
                .andExpect(jsonPath("$.flaggedCount").value(0));

        // Log low-rating question
        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Q", "topicSlug", "arrays", "selfRating", 2
                        ))))
                .andExpect(status().isCreated());

        // After: 1 flag
        mockMvc.perform(get("/api/progress").header("Authorization", auth))
                .andExpect(jsonPath("$.flaggedCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void logQuestion_noTopicSlug_stillLogsWithoutFlag() throws Exception {
        String auth = bearer();
        long intId  = createInterview(auth, "Dropbox");

        // Low rating but no topicSlug → no flag (topic unknown)
        mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "text", "Tell me about yourself", "selfRating", 1
                        ))))
                .andExpect(status().isCreated());

        assertThat(flagRepository.findAll()).isEmpty();
    }

    @Test
    void confidenceCannotGoBelowZero() throws Exception {
        // Set confidence to 10, then apply 3 low-rating questions → floor at 0
        testTopic.setConfidence(10);
        topicRepository.save(testTopic);

        String auth = bearer();
        long intId  = createInterview(auth, "Palantir");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/interviews/" + intId + "/questions")
                            .header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "text", "Q" + i, "topicSlug", "arrays", "selfRating", 1
                            ))))
                    .andExpect(status().isCreated());
        }

        int finalConf = getConfidence(testTopic.getId());
        assertThat(finalConf).isGreaterThanOrEqualTo(0);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private long createInterview(String auth, String company) throws Exception {
        String body = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "company", company, "role", "SWE", "stage", "onsite", "round", "Technical"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private int getConfidence(Long topicId) {
        return topicRepository.findById(topicId)
                .map(t -> t.getConfidence() != null ? t.getConfidence() : 0)
                .orElse(0);
    }
}

package com.interview.prep.platform.backend_core.ai;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.interview.prep.platform.backend_core.IntegrationTestBase;
import com.interview.prep.platform.backend_core.content.Question;
import com.interview.prep.platform.backend_core.content.QuestionRepository;
import com.interview.prep.platform.backend_core.interview.ReviewFlagRepository;
import com.interview.prep.platform.backend_core.study.Phase;
import com.interview.prep.platform.backend_core.study.PhaseRepository;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.study.Week;
import com.interview.prep.platform.backend_core.study.WeekRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice 4 — AI gateway: Java→Python HTTP contract.
 *
 * WireMock stubs the ai-service so we can verify exactly what Java sends and
 * how it maps the response back — without a running Python process.
 *
 * Contracts verified:
 *  - X-Service-Token / X-User-Id / X-Model headers are sent by AiClient
 *  - result + meta from Python pass through the /mock and /analyze endpoints
 *  - budgetWarning from Python passes through to the response envelope
 *  - 502 is returned when the ai-service errors
 *  - /api/ai/guide persists generated content, replacing previous AI rows
 *    while keeping manual rows (no duplicates on regenerate)
 *  - all /api/ai/** endpoints require auth
 */
class AiGatewayIT extends IntegrationTestBase {

    private static WireMockServer wiremock;

    @Autowired private PhaseRepository phaseRepository;
    @Autowired private WeekRepository weekRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private ReviewFlagRepository reviewFlagRepository;

    @DynamicPropertySource
    static void aiServiceUrl(DynamicPropertyRegistry registry) {
        wiremock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wiremock.start();
        registry.add("app.ai-service.url", wiremock::baseUrl);
        registry.add("app.ai-service.service-token", () -> "test-inter-service-token");
    }

    @BeforeEach
    void setUp() {
        baseSetUp();
        wiremock.resetAll();
    }

    @AfterAll
    static void tearDown() {
        if (wiremock != null) wiremock.stop();
    }

    // ── Auth guard ────────────────────────────────────────────────────────────

    @Test
    void aiEndpoints_requireAuth() throws Exception {
        mockMvc.perform(post("/api/ai/guide").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/mock").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/analyze").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── AiClient header contract ──────────────────────────────────────────────

    @Test
    void mock_sendsServiceTokenUserIdAndModelHeaders() throws Exception {
        stubPost("/ai/mock", Map.of(
                "result", Map.of("score", 4),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 80, "cost", 0.0001)));

        mockMvc.perform(post("/api/ai/mock")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "Explain quicksort."))))
                .andExpect(status().isOk());

        wiremock.verify(postRequestedFor(urlPathEqualTo("/ai/mock"))
                .withHeader("X-Service-Token", equalTo("test-inter-service-token"))
                .withHeader("X-User-Id", matching("\\d+"))
                .withHeader("X-Model", matching(".+")));
    }

    // ── Response pass-through ─────────────────────────────────────────────────

    @Test
    void analyze_passesThroughResultAndMeta() throws Exception {
        stubPost("/ai/analyze", Map.of(
                "result", Map.of("rating", 4, "summary", "Solid answer"),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 200, "cost", 0.0004)));

        mockMvc.perform(post("/api/ai/analyze")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "q", "answer", "a"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary").value("Solid answer"))
                .andExpect(jsonPath("$.meta.tokens").value(200));
    }

    @Test
    void mock_passesThroughBudgetWarningFromPython() throws Exception {
        stubPost("/ai/mock", Map.of(
                "result", Map.of("score", 3),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 100, "cost", 0.0),
                "budgetWarning", true));

        mockMvc.perform(post("/api/ai/mock")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetWarning").value(true));
    }

    // ── Python down → 502 ─────────────────────────────────────────────────────

    @Test
    void mock_pythonDown_returns502() throws Exception {
        wiremock.stubFor(WireMock.post(urlPathEqualTo("/ai/mock"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(post("/api/ai/mock")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway());
    }

    // ── Guide: persistence + replace-on-regenerate ────────────────────────────

    @Test
    void guide_unknownTopic_returns404() throws Exception {
        mockMvc.perform(post("/api/ai/guide")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("slug", "nope", "tab", "questions"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void guideQuestions_replacesAiRowsAndKeepsManualOnes() throws Exception {
        Topic topic = createTopic("arrays");

        Question manual = new Question();
        manual.setUserId(testUserId);
        manual.setTopicId(topic.getId());
        manual.setText("My own question?");
        manual.setDisplayOrder(0);
        questionRepository.save(manual);

        stubPost("/ai/guide", Map.of(
                "result", Map.of("questions", List.of(
                        Map.of("text", "What is the time complexity of binary search?"),
                        Map.of("text", "How does a two-pointer sweep work?"))),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 300, "cost", 0.0006)));

        String body = objectMapper.writeValueAsString(Map.of("slug", "arrays", "tab", "questions"));

        mockMvc.perform(post("/api/ai/guide")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        List<Question> afterFirst = questionRepository
                .findByUserIdAndTopicIdOrderByDisplayOrderAsc(testUserId, topic.getId());
        assertThat(afterFirst).hasSize(3);

        // Regenerate: AI rows are replaced, not appended; the manual row survives
        mockMvc.perform(post("/api/ai/guide")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        List<Question> afterSecond = questionRepository
                .findByUserIdAndTopicIdOrderByDisplayOrderAsc(testUserId, topic.getId());
        assertThat(afterSecond).hasSize(3);
        assertThat(afterSecond).extracting(Question::getText).contains("My own question?");
        assertThat(afterSecond).filteredOn(q -> "manual".equals(q.getSource())).hasSize(1);
    }

    // ── Mock interview: start → turn → completion feeds the loop ─────────────

    @Test
    void mockFlow_startTurnComplete_lowScoreFlagsTopic() throws Exception {
        Topic topic = createTopic("arrays");
        String auth = bearer();

        stubPost("/ai/mock", Map.of(
                "result", Map.of("reply", "Tell me about binary search.", "done", false),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 100, "cost", 0.0)));

        String startBody = mockMvc.perform(post("/api/mocks/start")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "technical", "topicSlug", "arrays"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transcript[0].text").value("Tell me about binary search."))
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(startBody).path("id").asLong();

        // Interviewer concludes with a low score on the next turn
        wiremock.resetAll();
        stubPost("/ai/mock", Map.of(
                "result", Map.of("reply", "That's all — thanks.", "done", true, "score", 2,
                        "feedback", "**What went well** ..."),
                "meta", Map.of("model", "claude-haiku-4-5", "cached", false, "tokens", 150, "cost", 0.0)));

        mockMvc.perform(post("/api/mocks/" + sessionId + "/turn")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "I don't know."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.done").value(true))
                .andExpect(jsonPath("$.result.score").value(2));

        // Session persisted as completed with feedback
        mockMvc.perform(get("/api/mocks/" + sessionId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.feedback.score").value(2));

        // Feedback loop: score <= 2 creates a review flag and lowers confidence
        assertThat(reviewFlagRepository.findByUserIdAndTopicIdAndResolved(testUserId, topic.getId(), false))
                .hasSize(1);
        assertThat(topicRepository.findById(topic.getId()).orElseThrow().getConfidence()).isEqualTo(50);

        // Turns on a finished session are rejected
        mockMvc.perform(post("/api/mocks/" + sessionId + "/turn")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answer", "one more"))))
                .andExpect(status().isConflict());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Topic createTopic(String slug) {
        Phase phase = new Phase();
        phase.setUserId(testUserId);
        phase.setCode("phase-1");
        phase.setName("Phase 1");
        phase.setDisplayOrder(0);
        phase = phaseRepository.save(phase);

        Week week = new Week();
        week.setUserId(testUserId);
        week.setPhase(phase);
        week.setCode("w-1");
        week.setTitle("Week 1");
        week.setDisplayOrder(0);
        week = weekRepository.save(week);

        Topic topic = new Topic();
        topic.setUserId(testUserId);
        topic.setWeek(week);
        topic.setCode("t-1");
        topic.setSlug(slug);
        topic.setTitle("Arrays & two pointers");
        topic.setCategory("dsa");
        topic.setDisplayOrder(0);
        return topicRepository.save(topic);
    }

    private void stubPost(String path, Map<String, Object> body) throws Exception {
        wiremock.stubFor(WireMock.post(urlPathEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(body))));
    }
}

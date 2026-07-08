package com.interview.prep.platform.backend_core.ingestion;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.interview.prep.platform.backend_core.IntegrationTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gmail sync against a WireMock'd Gmail API:
 *  - OAuth refresh-token flow fetches an access token
 *  - only classifier-matched emails are stored (personal mail is dropped)
 *  - message-id dedupe makes a second sync a no-op
 */
class GmailSyncIT extends IntegrationTestBase {

    private static WireMockServer wiremock;

    @DynamicPropertySource
    static void gmailProps(DynamicPropertyRegistry registry) {
        wiremock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wiremock.start();
        registry.add("app.gmail.enabled", () -> "true");
        registry.add("app.gmail.api-base-url", wiremock::baseUrl);
        registry.add("app.gmail.token-url", () -> wiremock.baseUrl() + "/oauth/token");
        registry.add("app.gmail.client-id", () -> "test-client");
        registry.add("app.gmail.client-secret", () -> "test-secret");
        registry.add("app.gmail.refresh-token", () -> "test-refresh");
    }

    @BeforeEach
    void setUp() throws Exception {
        baseSetUp();
        wiremock.resetAll();

        wiremock.stubFor(WireMock.post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson(Map.of("access_token", "test-access", "expires_in", 3600))));

        wiremock.stubFor(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages"))
                .willReturn(okJson(Map.of(
                        "messages", List.of(Map.of("id", "job-1"), Map.of("id", "personal-1")),
                        "resultSizeEstimate", 2))));

        wiremock.stubFor(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/job-1"))
                .willReturn(okJson(message("job-1",
                        "Greenhouse <no-reply@greenhouse.io>",
                        "Your application to Stripe",
                        "Thanks for applying to the Senior Backend Engineer role at Stripe."))));

        wiremock.stubFor(WireMock.get(urlPathEqualTo("/gmail/v1/users/me/messages/personal-1"))
                .willReturn(okJson(message("personal-1",
                        "Mom <mom@gmail.com>",
                        "Dinner on Sunday?",
                        "Also your bank OTP is 123456 — sensitive stuff."))));
    }

    @AfterAll
    static void tearDown() {
        if (wiremock != null) wiremock.stop();
    }

    @Test
    void sync_ingestsOnlyJobEmails_andDedupesOnSecondRun() throws Exception {
        String auth = bearer();

        mockMvc.perform(post("/api/jobs/sync").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.matched").value(1))
                .andExpect(jsonPath("$.ingested").value(1));

        // only the job email was stored; the personal one never touched the DB
        mockMvc.perform(get("/api/jobs").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Stripe"));

        // second sync: same message ids → dedupe
        mockMvc.perform(post("/api/jobs/sync").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingested").value(0));
    }

    @Test
    void sync_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/jobs/sync")).andExpect(status().isUnauthorized());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(Map<String, Object> body)
            throws Exception {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> message(String id, String from, String subject, String body) {
        String data = Base64.getUrlEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return Map.of(
                "id", id,
                "payload", Map.of(
                        "mimeType", "text/plain",
                        "headers", List.of(
                                Map.of("name", "From", "value", from),
                                Map.of("name", "Subject", "value", subject)),
                        "body", Map.of("data", data)));
    }
}

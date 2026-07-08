package com.interview.prep.platform.backend_core.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gmail REST client (no SDK) — OAuth2 refresh-token flow.
 *
 * Privacy: this client never lists the whole mailbox. The search query is either
 * a user-managed label (app.gmail.label — the user routes job mail to it with a
 * Gmail filter) or a sender allowlist of known job boards/ATS domains. Anything
 * outside that query is never fetched, so it can't be stored or sent anywhere.
 *
 * Credentials (OAuth client id/secret + refresh token) come from Vault via
 * property placeholders — never from code or checked-in config.
 */
@Component
@ConditionalOnProperty("app.gmail.enabled")
@Slf4j
public class GmailClient {

    public record GmailMessage(String id, String from, String subject, String body) {}

    @Value("${app.gmail.client-id:}")
    private String clientId;
    @Value("${app.gmail.client-secret:}")
    private String clientSecret;
    @Value("${app.gmail.refresh-token:}")
    private String refreshToken;
    @Value("${app.gmail.api-base-url:https://gmail.googleapis.com}")
    private String apiBaseUrl;
    @Value("${app.gmail.token-url:https://oauth2.googleapis.com/token}")
    private String tokenUrl;
    @Value("${app.gmail.label:}")
    private String label;
    @Value("${app.gmail.lookback-days:7}")
    private int lookbackDays;
    @Value("${app.gmail.max-results:25}")
    private int maxResults;

    private final RestClient http = RestClient.create();

    private volatile String accessToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    @SuppressWarnings("unchecked")
    public List<GmailMessage> fetchJobEmails() {
        String token = accessToken();
        Map<String, Object> list = http.get()
                .uri(apiBaseUrl + "/gmail/v1/users/me/messages?q={q}&maxResults={n}", buildQuery(), maxResults)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(Map.class);

        List<GmailMessage> out = new ArrayList<>();
        if (list == null || !(list.get("messages") instanceof List<?> ids)) return out;

        for (Object o : ids) {
            if (!(o instanceof Map<?, ?> ref) || !(ref.get("id") instanceof String id)) continue;
            try {
                Map<String, Object> msg = http.get()
                        .uri(apiBaseUrl + "/gmail/v1/users/me/messages/{id}?format=full", id)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .body(Map.class);
                if (msg != null && msg.get("payload") instanceof Map<?, ?> payload) {
                    out.add(new GmailMessage(id,
                            header((Map<String, Object>) payload, "From"),
                            header((Map<String, Object>) payload, "Subject"),
                            extractBody((Map<String, Object>) payload)));
                }
            } catch (Exception e) {
                log.warn("Gmail: could not fetch message {}: {}", id, e.getMessage());
            }
        }
        return out;
    }

    /** Visible for tests. */
    String buildQuery() {
        if (label != null && !label.isBlank()) {
            return "label:" + label.strip();
        }
        String senders = JobEmailClassifier.SENDER_ALLOWLIST.stream()
                .map(d -> "from:" + d)
                .collect(Collectors.joining(" "));
        return "newer_than:" + lookbackDays + "d {" + senders + "}";
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return accessToken;
        }
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    "Gmail OAuth is not configured — put gmail.client.id / gmail.client.secret / gmail.refresh.token in Vault");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        Map<String, Object> resp = http.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (resp == null || !(resp.get("access_token") instanceof String at)) {
            throw new IllegalStateException("Gmail token refresh returned no access_token");
        }
        accessToken = at;
        long expiresIn = resp.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);
        return accessToken;
    }

    // ── message JSON helpers ────────────────────────────────────────────────────

    private static String header(Map<String, Object> payload, String name) {
        if (payload.get("headers") instanceof List<?> headers) {
            for (Object h : headers) {
                if (h instanceof Map<?, ?> m && name.equalsIgnoreCase(String.valueOf(m.get("name")))) {
                    Object value = m.get("value");
                    return value != null ? String.valueOf(value) : "";
                }
            }
        }
        return "";
    }

    private static String extractBody(Map<String, Object> payload) {
        String plain = findPart(payload, "text/plain");
        if (plain != null) return plain;
        String html = findPart(payload, "text/html");
        if (html != null) {
            return html.replaceAll("(?s)<style[^>]*>.*?</style>", " ")
                    .replaceAll("(?s)<[^>]*>", " ")
                    .replaceAll("\\s+", " ")
                    .strip();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String findPart(Map<String, Object> part, String mimeType) {
        if (mimeType.equals(part.get("mimeType"))
                && part.get("body") instanceof Map<?, ?> body
                && body.get("data") instanceof String data && !data.isBlank()) {
            return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
        }
        if (part.get("parts") instanceof List<?> parts) {
            for (Object p : parts) {
                if (p instanceof Map<?, ?> m) {
                    String found = findPart((Map<String, Object>) m, mimeType);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}

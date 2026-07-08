package com.interview.prep.platform.backend_core.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.ai.AiGatewayService;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import com.interview.prep.platform.backend_core.interview.FeedbackLoopService;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import com.interview.prep.platform.backend_core.user.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockService {

    private final MockSessionRepository mockSessionRepository;
    private final TopicRepository topicRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final AiClient aiClient;
    private final AiGatewayService aiGatewayService;
    private final FeedbackLoopService feedbackLoopService;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    public Map<String, Object> start(Long userId, Map<String, Object> request) {
        String type = request.get("type") instanceof String s && !s.isBlank() ? s : "technical";
        String topicSlug = request.get("topicSlug") instanceof String s && !s.isBlank() ? s : null;

        MockSession session = new MockSession();
        session.setUserId(userId);
        session.setType(type);
        session.setTopicSlug(topicSlug);
        session = mockSessionRepository.save(session);

        Map<String, Object> response = aiClient.post(userId, "/ai/mock",
                aiRequestBody(userId, session, List.of()), "mock-start");
        Map<String, Object> result = resultOf(response);
        String reply = str(result, "reply");

        List<Map<String, String>> turns = new ArrayList<>();
        turns.add(Map.of("role", "ai", "text", reply));
        session.setTurnsJson(objectMapper.writeValueAsString(turns));
        mockSessionRepository.save(session);

        Map<String, Object> out = new HashMap<>();
        out.put("id", session.getId());
        out.put("transcript", turns);
        out.put("budgetWarning", response.getOrDefault("budgetWarning", false));
        return out;
    }

    @Transactional
    @SneakyThrows
    public Map<String, Object> turn(Long userId, Long sessionId, String answer) {
        if (answer == null || answer.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, HttpStatus.UNPROCESSABLE_ENTITY, "answer is required");
        }
        MockSession session = mockSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!"in_progress".equals(session.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Session already finished");
        }

        List<Map<String, String>> turns = session.getTurnsJson() != null
                ? objectMapper.readValue(session.getTurnsJson(), new TypeReference<>() {})
                : new ArrayList<>();
        turns.add(Map.of("role", "user", "text", answer));

        // history in provider message format: ai → assistant
        List<Map<String, String>> history = turns.stream()
                .map(t -> Map.of(
                        "role", "ai".equals(t.get("role")) ? "assistant" : "user",
                        "content", t.get("text")))
                .toList();

        Map<String, Object> response = aiClient.post(userId, "/ai/mock",
                aiRequestBody(userId, session, history), "mock-turn");
        Map<String, Object> result = resultOf(response);

        turns.add(Map.of("role", "ai", "text", str(result, "reply")));
        session.setTurnsJson(objectMapper.writeValueAsString(turns));

        boolean done = Boolean.TRUE.equals(result.get("done"));
        if (done) {
            session.setStatus("completed");
            session.setFinishedAt(Instant.now());
            Integer score = result.get("score") instanceof Number n ? n.intValue() : null;
            session.setFeedbackJson(objectMapper.writeValueAsString(Map.of(
                    "score", score != null ? score : 0,
                    "feedback", str(result, "feedback"))));
            if (score != null && session.getTopicSlug() != null) {
                topicRepository.findByUserIdAndSlug(userId, session.getTopicSlug())
                        .ifPresent(topic -> feedbackLoopService.handleMockScore(userId, topic.getId(), score));
            }
        }
        mockSessionRepository.save(session);

        Map<String, Object> out = new HashMap<>();
        out.put("result", result);
        out.put("budgetWarning", response.getOrDefault("budgetWarning", false));
        return out;
    }

    @SneakyThrows
    public Map<String, Object> get(Long userId, Long id) {
        MockSession session = mockSessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        Map<String, Object> out = new HashMap<>();
        out.put("id", session.getId());
        out.put("type", session.getType());
        out.put("topicSlug", session.getTopicSlug());
        out.put("status", session.getStatus());
        out.put("turns", session.getTurnsJson() != null
                ? objectMapper.readValue(session.getTurnsJson(), Object.class) : List.of());
        out.put("feedback", session.getFeedbackJson() != null
                ? objectMapper.readValue(session.getFeedbackJson(), Object.class) : Map.of());
        return out;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Map<String, Object> aiRequestBody(Long userId, MockSession session,
                                              List<Map<String, String>> history) {
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        String targetRole = settings != null && settings.getTargetRole() != null
                ? settings.getTargetRole() : "Software Engineer";
        String seniority = settings != null && settings.getTargetLevel() != null
                ? settings.getTargetLevel() : "mid";

        // topic: real topic title when the session targets one, else the session type
        String topic = session.getTopicSlug() != null
                ? topicRepository.findByUserIdAndSlug(userId, session.getTopicSlug())
                        .map(t -> t.getTitle()).orElse(session.getTopicSlug())
                : session.getType();

        Map<String, Object> body = new HashMap<>();
        body.put("target_role", targetRole);
        body.put("topic", topic);
        body.put("difficulty", "medium");
        body.put("interview_type", switch (session.getType()) {
            case "system-design" -> "system_design";
            case "behavioral" -> "behavioral";
            default -> "technical";
        });
        body.put("seniority", seniority);
        body.put("strengths", aiGatewayService.profileSkills(userId));
        body.put("history", history);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultOf(Map<String, Object> response) {
        if (response != null && response.get("result") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                "AI service returned no result for the mock turn");
    }

    private static String str(Map<String, Object> m, String key) {
        return m.get(key) instanceof String s ? s : "";
    }
}

package com.interview.prep.platform.backend_core.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MockService {

    private final MockSessionRepository mockSessionRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    public Map<String, Object> start(Long userId, Map<String, Object> request) {
        String topicSlug = (String) request.get("topicSlug");
        MockSession session = new MockSession();
        session.setUserId(userId);
        session.setTopicSlug(topicSlug != null ? topicSlug : "general");
        mockSessionRepository.save(session);

        Map<String, Object> result = aiClient.post(userId, "/ai/mock",
                Map.of("sessionId", session.getId(), "topicSlug", session.getTopicSlug()), "mock-start");

        session.setTurnsJson(objectMapper.writeValueAsString(result.get("turns")));
        mockSessionRepository.save(session);
        result.put("sessionId", session.getId());
        return result;
    }

    @SneakyThrows
    public Map<String, Object> get(Long userId, Long id) {
        MockSession session = mockSessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND));
        return Map.of(
                "id", session.getId(),
                "topicSlug", session.getTopicSlug(),
                "status", session.getStatus(),
                "turns", session.getTurnsJson() != null
                        ? objectMapper.readValue(session.getTurnsJson(), Object.class) : java.util.List.of(),
                "feedback", session.getFeedbackJson() != null
                        ? objectMapper.readValue(session.getFeedbackJson(), Object.class) : Map.of());
    }
}

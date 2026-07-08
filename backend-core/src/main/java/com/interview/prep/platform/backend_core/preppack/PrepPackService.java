package com.interview.prep.platform.backend_core.preppack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.AiClient;
import com.interview.prep.platform.backend_core.interview.Interview;
import com.interview.prep.platform.backend_core.interview.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrepPackService {

    private final PrepPackRepository prepPackRepository;
    private final InterviewService interviewService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Transactional
    @SneakyThrows
    public Map<String, Object> getOrCreate(Long userId, Long interviewId) {
        return prepPackRepository.findByInterviewIdAndUserId(interviewId, userId)
                .map(this::toMap)
                .orElseGet(() -> generate(userId, interviewId));
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private Map<String, Object> generate(Long userId, Long interviewId) {
        Interview interview = interviewService.requireOwned(userId, interviewId);
        Map<String, Object> payload = Map.of(
                "company", interview.getCompany(),
                "role", interview.getRole(),
                "jdText", interview.getJdText() != null ? interview.getJdText() : "");
        Map<String, Object> aiResponse = aiClient.post(userId, "/ai/agents/prep-pack", payload, "prep-pack");

        // AI agent response wraps data under "result" (string from agent loop or nested map)
        Map<String, Object> data;
        Object resultObj = aiResponse.get("result");
        if (resultObj instanceof String s) {
            data = objectMapper.readValue(s, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } else if (resultObj instanceof Map<?, ?> m) {
            data = (Map<String, Object>) m;
        } else {
            data = java.util.Map.of();
        }

        PrepPack pack = new PrepPack();
        pack.setUserId(userId); pack.setInterviewId(interviewId);
        pack.setTopicsJson(objectMapper.writeValueAsString(data.getOrDefault("topics", java.util.List.of())));
        pack.setQuestionsJson(objectMapper.writeValueAsString(data.getOrDefault("questions", java.util.List.of())));
        pack.setTipsJson(objectMapper.writeValueAsString(data.getOrDefault("tips", java.util.List.of())));
        prepPackRepository.save(pack);
        return data;
    }

    @SneakyThrows
    private Map<String, Object> toMap(PrepPack pack) {
        return Map.of(
                "id", pack.getId(),
                "topics", objectMapper.readValue(pack.getTopicsJson() != null ? pack.getTopicsJson() : "[]", Object.class),
                "questions", objectMapper.readValue(pack.getQuestionsJson() != null ? pack.getQuestionsJson() : "[]", Object.class),
                "tips", objectMapper.readValue(pack.getTipsJson() != null ? pack.getTipsJson() : "[]", Object.class));
    }
}

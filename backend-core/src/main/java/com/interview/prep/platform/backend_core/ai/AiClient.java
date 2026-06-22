package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiClient {

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;
    private final BudgetGuard budgetGuard;

    public Map<String, Object> get(Long userId, String path, String feature) {
        String model = budgetGuard.resolveModel(userId);
        boolean warning = budgetGuard.isBudgetWarning(userId);
        try {
            Map<String, Object> response = aiRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParam("model", model)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (response != null && warning) {
                response = new java.util.HashMap<>(response);
                response.put("budgetWarning", true);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        }
    }

    public Map<String, Object> post(Long userId, String path, Object body, String feature) {
        String model = budgetGuard.resolveModel(userId);
        boolean warning = budgetGuard.isBudgetWarning(userId);
        try {
            Map<String, Object> payload;
            if (body instanceof Map<?,?> m) {
                payload = new java.util.HashMap<>();
                m.forEach((k, v) -> payload.put(String.valueOf(k), v));
            } else {
                payload = objectMapper.convertValue(body, new TypeReference<>() {});
            }
            payload.put("model", model);

            Map<String, Object> response = aiRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (response != null && warning) {
                response = new java.util.HashMap<>(response);
                response.put("budgetWarning", true);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        }
    }
}

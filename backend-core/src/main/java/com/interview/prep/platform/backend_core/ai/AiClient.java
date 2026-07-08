package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.common.error.ApiException;
import com.interview.prep.platform.backend_core.common.error.DesktopModeException;
import com.interview.prep.platform.backend_core.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
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
        String provider = budgetGuard.resolveProvider(userId);
        String ollamaUrl = "ollama".equals(provider) ? budgetGuard.resolveOllamaUrl(userId) : null;
        boolean warning = budgetGuard.isBudgetWarning(userId);
        try {
            var getReq = aiRestClient.get()
                    .uri(path)
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Provider", provider)
                    .header("X-Model", model);
            if (ollamaUrl != null) getReq = getReq.header("X-Ollama-Url", ollamaUrl);
            Map<String, Object> response = getReq
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (response != null && "desktop".equals(response.get("mode"))) {
                throw new DesktopModeException((String) response.getOrDefault("desktop_prompt", ""));
            }
            if (response != null && warning) {
                response = new java.util.HashMap<>(response);
                response.put("budgetWarning", true);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        } catch (HttpStatusCodeException e) {
            log.warn("AI service error on GET {}: {}", path, e.getStatusCode());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        }
    }

    public Map<String, Object> post(Long userId, String path, Object body, String feature) {
        return post(userId, path, body, feature, null, null, null);
    }

    public Map<String, Object> post(Long userId, String path, Object body, String feature,
                                     String providerOverride, String ollamaUrlOverride) {
        return post(userId, path, body, feature, providerOverride, ollamaUrlOverride, null);
    }

    public Map<String, Object> post(Long userId, String path, Object body, String feature,
                                     String providerOverride, String ollamaUrlOverride, String modelOverride) {
        String model = modelOverride != null ? modelOverride : budgetGuard.resolveModel(userId);
        String provider = providerOverride != null ? providerOverride : budgetGuard.resolveProvider(userId);
        String ollamaUrl = ollamaUrlOverride != null ? ollamaUrlOverride
                : ("ollama".equals(provider) ? budgetGuard.resolveOllamaUrl(userId) : null);
        boolean warning = budgetGuard.isBudgetWarning(userId);
        try {
            Map<String, Object> payload;
            if (body instanceof Map<?,?> m) {
                payload = new java.util.HashMap<>();
                m.forEach((k, v) -> payload.put(String.valueOf(k), v));
            } else {
                payload = objectMapper.convertValue(body, new TypeReference<>() {});
            }

            var req = aiRestClient.post()
                    .uri(path)
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Provider", provider)
                    .header("X-Model", model);
            if (ollamaUrl != null) req = req.header("X-Ollama-Url", ollamaUrl);

            Map<String, Object> response = req
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (response != null && "desktop".equals(response.get("mode"))) {
                throw new DesktopModeException((String) response.getOrDefault("desktop_prompt", ""));
            }
            if (response != null && warning) {
                response = new java.util.HashMap<>(response);
                response.put("budgetWarning", true);
            }
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        } catch (HttpStatusCodeException e) {
            log.warn("AI service error on POST {}: {}", path, e.getStatusCode());
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE, HttpStatus.BAD_GATEWAY);
        }
    }
}

package com.interview.prep.platform.backend_core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    @Value("${app.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${app.ai-service.service-token:dev-service-token}")
    private String serviceToken;

    // 30-minute timeout — a comprehensive plan on a local 27B can exceed 10 min per call
    private static final int AI_TIMEOUT_MS = 1_800_000;

    @Bean
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(AI_TIMEOUT_MS);
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .requestFactory(factory)
                .defaultHeader("X-Service-Token", serviceToken)
                .build();
    }
}

package com.interview.prep.platform.backend_core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    @Value("${app.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${app.ai-service.service-token:dev-service-token}")
    private String serviceToken;

    @Bean
    public RestClient aiRestClient() {
        return RestClient.builder()
                .baseUrl(aiServiceUrl)
                .defaultHeader("X-Service-Token", serviceToken)
                .build();
    }
}

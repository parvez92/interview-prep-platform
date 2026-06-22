package com.interview.prep.platform.backend_core.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Stub Gmail client — wired only when app.gmail.enabled=true.
 * OAuth flow and real API calls are wired in a later iteration.
 */
@Component
@ConditionalOnProperty("app.gmail.enabled")
@Slf4j
public class GmailClient {

    @Value("${app.gmail.user-email:}")
    private String userEmail;

    public List<Map<String, String>> fetchJobEmails() {
        log.info("GmailClient: polling for job alert emails for {}", userEmail);
        return List.of();
    }
}

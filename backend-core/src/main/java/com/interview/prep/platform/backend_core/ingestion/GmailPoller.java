package com.interview.prep.platform.backend_core.ingestion;

import com.interview.prep.platform.backend_core.jobs.JobService;
import com.interview.prep.platform.backend_core.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty("app.gmail.enabled")
@RequiredArgsConstructor
@Slf4j
public class GmailPoller {

    private final GmailClient gmailClient;
    private final JobService jobService;
    private final AppUserRepository userRepository;

    @Scheduled(fixedDelayString = "${app.gmail.poll-interval-ms:1800000}")
    public void poll() {
        log.debug("GmailPoller: polling");
        userRepository.findAll().forEach(user -> {
            try {
                gmailClient.fetchJobEmails().forEach(email -> {
                    String msgId = email.get("id");
                    String company = email.getOrDefault("company", "Unknown");
                    String role = email.getOrDefault("role", "Unknown");
                    String jdText = email.get("body");
                    jobService.ingestFromEmail(user.getId(), msgId, company, role, jdText);
                });
            } catch (Exception e) {
                log.warn("GmailPoller: error for user {}: {}", user.getId(), e.getMessage());
            }
        });
    }
}

package com.interview.prep.platform.backend_core.ingestion;

import com.interview.prep.platform.backend_core.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("app.gmail.enabled")
@RequiredArgsConstructor
@Slf4j
public class GmailPoller {

    private final GmailSyncService gmailSyncService;
    private final AppUserRepository userRepository;

    @Scheduled(fixedDelayString = "${app.gmail.poll-interval-ms:1800000}")
    public void poll() {
        // single-user app: the mailbox belongs to the one account
        userRepository.findAll().stream().findFirst().ifPresentOrElse(
                user -> {
                    try {
                        gmailSyncService.syncNow(user.getId());
                    } catch (Exception e) {
                        log.warn("GmailPoller: sync failed: {}", e.getMessage());
                    }
                },
                () -> log.debug("GmailPoller: no users registered yet"));
    }
}

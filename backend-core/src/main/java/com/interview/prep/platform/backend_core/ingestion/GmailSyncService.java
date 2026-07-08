package com.interview.prep.platform.backend_core.ingestion;

import com.interview.prep.platform.backend_core.jobs.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Fetch → classify → ingest. Emails the classifier rejects are dropped on the
 * spot (never stored); accepted ones are reduced to company/role/JD text before
 * they touch the database. No LLM is involved anywhere in this path.
 */
@Service
@ConditionalOnProperty("app.gmail.enabled")
@RequiredArgsConstructor
@Slf4j
public class GmailSyncService {

    private final GmailClient gmailClient;
    private final JobEmailClassifier classifier;
    private final JobService jobService;

    public Map<String, Object> syncNow(Long userId) {
        List<GmailClient.GmailMessage> messages = gmailClient.fetchJobEmails();
        int matched = 0;
        int ingested = 0;
        for (GmailClient.GmailMessage msg : messages) {
            var job = classifier.classify(msg);
            if (job.isEmpty()) continue;
            matched++;
            if (jobService.ingestFromEmail(userId, msg.id(),
                    job.get().company(), job.get().role(), job.get().jdText()) != null) {
                ingested++;
            }
        }
        log.info("Gmail sync: fetched={} matched={} ingested={}", messages.size(), matched, ingested);
        return Map.of("fetched", messages.size(), "matched", matched, "ingested", ingested);
    }
}

package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpacedRepetitionService {

    private final TopicRepository topicRepository;

    /** Daily decay: topics not reviewed in >7 days lose 1 confidence point. */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void decayConfidence() {
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Topic> stale = topicRepository.findAll().stream()
                .filter(t -> t.getConfidence() != null && t.getConfidence() > 0)
                .filter(t -> t.getLastReviewedAt() == null || t.getLastReviewedAt().isBefore(threshold))
                .toList();
        stale.forEach(t -> t.setConfidence(Math.max(0, t.getConfidence() - 1)));
        topicRepository.saveAll(stale);
        log.debug("Spaced repetition: decayed {} topics", stale.size());
    }
}

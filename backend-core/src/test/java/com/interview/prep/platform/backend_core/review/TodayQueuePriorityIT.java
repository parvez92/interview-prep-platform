package com.interview.prep.platform.backend_core.review;

import com.interview.prep.platform.backend_core.IntegrationTestBase;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfile;
import com.interview.prep.platform.backend_core.onboarding.ResumeProfileRepository;
import com.interview.prep.platform.backend_core.study.Phase;
import com.interview.prep.platform.backend_core.study.PhaseRepository;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.study.Week;
import com.interview.prep.platform.backend_core.study.WeekRepository;
import com.interview.prep.platform.backend_core.user.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan-amendment-01 §1.5: once the plan is ≥5 days behind pace, optional (low-priority)
 * topics drop out of the Today Queue so the day's budget goes to the interview core.
 */
class TodayQueuePriorityIT extends IntegrationTestBase {

    @Autowired PhaseRepository phaseRepository;
    @Autowired WeekRepository weekRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired ResumeProfileRepository resumeProfileRepository;

    private Week week;

    @BeforeEach
    void setUp() {
        baseSetUp();
        topicRepository.deleteAll();
        weekRepository.deleteAll();
        phaseRepository.deleteAll();
        resumeProfileRepository.deleteAll();

        UserSettings settings = settingsRepository.findByUserId(testUserId).orElseThrow();
        settings.setHoursPerWeek(12);
        settingsRepository.save(settings);

        Phase phase = new Phase();
        phase.setUserId(testUserId);
        phase.setCode("phase-1");
        phase.setName("Java Core");
        phase.setDisplayOrder(0);
        phaseRepository.save(phase);

        week = new Week();
        week.setUserId(testUserId);
        week.setPhase(phase);
        week.setCode("w-1-1");
        week.setTitle("Week 1");
        week.setDisplayOrder(0);
        weekRepository.save(week);

        topic("high-core", "high");
        topic("low-optional", "low");
    }

    private void topic(String slug, String priority) {
        Topic t = new Topic();
        t.setUserId(testUserId);
        t.setWeek(week);
        t.setCode(slug.toUpperCase());
        t.setSlug(slug);
        t.setTitle(slug);
        t.setTag("new");
        t.setSource("standard");
        t.setPriority(priority);
        t.setStatus("todo");
        t.setEstMinutes(45);
        t.setDisplayOrder(slug.startsWith("high") ? 0 : 1);
        topicRepository.save(t);
    }

    /** planStart drives pace; the queue reads it from the résumé profile's updatedAt. */
    private void planStartedDaysAgo(int days) {
        ResumeProfile p = new ResumeProfile();
        p.setUserId(testUserId);
        p.setUpdatedAt(Instant.now().minus(days, ChronoUnit.DAYS));
        resumeProfileRepository.save(p);
    }

    @Test
    @DisplayName("on pace: both the core and the optional topic are queued")
    void onPace_includesLow() throws Exception {
        planStartedDaysAgo(0);
        mockMvc.perform(get("/api/review/today").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.velocity").value("on pace"))
                .andExpect(jsonPath("$.items[?(@.topicSlug == 'high-core')]").exists())
                .andExpect(jsonPath("$.items[?(@.topicSlug == 'low-optional')]").exists());
    }

    @Test
    @DisplayName("far behind: the optional topic is dropped, the core stays")
    void behindPace_dropsLow() throws Exception {
        // 60 days elapsed on a 1-week plan with nothing done → deeply behind (≥5 days)
        planStartedDaysAgo(60);
        mockMvc.perform(get("/api/review/today").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.velocity").value(org.hamcrest.Matchers.containsString("behind")))
                .andExpect(jsonPath("$.items[?(@.topicSlug == 'high-core')]").exists())
                .andExpect(jsonPath("$.items[?(@.topicSlug == 'low-optional')]").doesNotExist());
    }
}

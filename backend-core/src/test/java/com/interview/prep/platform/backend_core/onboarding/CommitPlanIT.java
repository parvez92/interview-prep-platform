package com.interview.prep.platform.backend_core.onboarding;

import com.interview.prep.platform.backend_core.IntegrationTestBase;
import com.interview.prep.platform.backend_core.study.Topic;
import com.interview.prep.platform.backend_core.study.TopicRepository;
import com.interview.prep.platform.backend_core.study.Week;
import com.interview.prep.platform.backend_core.study.WeekRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT /api/onboarding/plan/commit against a hand-authored plan: the raw v2 shape
 * (`coarse_topics`, `source`, week `bridge`/`anchor`) rather than the frontend-normalised one.
 */
class CommitPlanIT extends IntegrationTestBase {

    @Autowired ResumeProfileRepository resumeProfileRepository;
    @Autowired WeekRepository weekRepository;
    @Autowired TopicRepository topicRepository;

    @BeforeEach
    void setUp() {
        baseSetUp();
        resumeProfileRepository.deleteAll();

        ResumeProfile profile = new ResumeProfile();
        profile.setUserId(testUserId);
        profile.setConfirmedJson("""
                {"skills":[{"name":"Java","level":"expert","years":11},
                           {"name":"Kubernetes","level":"intermediate","years":3}]}""");
        resumeProfileRepository.save(profile);
    }

    private String plan() throws Exception {
        return objectMapper.writeValueAsString(Map.of("phases", List.of(Map.of(
                "name", "Technical Skills Depth Review — Java Core",
                "goal", "Audit a decade of claimed Java expertise.",
                "weeks_detail", List.of(Map.of(
                        "week_number", 1,
                        "title", "JVM, memory & GC",
                        "bridge", "Start where 11 years of production Java should be rock-solid.",
                        "anchor", "the JVM model your event-driven services live on",
                        "coarse_topics", List.of(
                                Map.of("title", "Heap/stack layout & the generational hypothesis",
                                        "category", "language", "source", "resume", "priority", "high",
                                        "scope", "Eden, survivor spaces, TLAB, card tables",
                                        "split_hint", "likely"),
                                Map.of("title", "Virtual threads & structured concurrency",
                                        "category", "language", "source", "standard", "priority", "low",
                                        "scope", "carrier threads, pinning, Loom",
                                        "split_hint", "maybe"),
                                // no priority field → defaults to high
                                Map.of("title", "Kubernetes probes — liveness, readiness",
                                        "category", "cloud", "source", "interest",
                                        "scope", "startup probe, HPA",
                                        "split_hint", "none"))))))));
    }

    @Test
    @DisplayName("raw v2 plan commits: coarse_topics persist, bridge/anchor survive, tag is derived")
    void commitsHandAuthoredPlan() throws Exception {
        mockMvc.perform(put("/api/onboarding/plan/commit")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plan()))
                .andExpect(status().isOk());

        // coarse_topics is read even though the frontend would have renamed it to "topics"
        List<Topic> topics = topicRepository.findByUserIdWithWeekOrdered(testUserId);
        assertThat(topics).hasSize(3);

        Week week = weekRepository.findById(topics.get(0).getWeek().getId()).orElseThrow();
        assertThat(week.getTitle()).isEqualTo("Week 1 — JVM, memory & GC");
        assertThat(week.getBridge()).contains("rock-solid");
        assertThat(week.getAnchor()).contains("event-driven services");

        // source comes from the plan; tag is derived from the résumé, never echoed as "new"
        assertThat(topics).extracting(Topic::getTitle, Topic::getSource, Topic::getTag)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "Heap/stack layout & the generational hypothesis", "resume", "exp"),
                        org.assertj.core.groups.Tuple.tuple(
                                "Virtual threads & structured concurrency", "standard", "new"),
                        org.assertj.core.groups.Tuple.tuple(
                                "Kubernetes probes — liveness, readiness", "interest", "refresh"));

        assertThat(topics).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo("todo");
            assertThat(t.getConfidence()).isNull();
            assertThat(t.getLastReviewedAt()).isNull();
        });
        assertThat(topics.get(0).getSplitHint()).isEqualTo("likely");
        assertThat(topics.get(0).getAngle()).contains("TLAB");

        // priority persists from the plan; an absent field defaults to high
        assertThat(topics).extracting(Topic::getPriority)
                .containsExactly("high", "low", "high");
    }

    @Test
    @DisplayName("committing again replaces the previous plan rather than appending")
    void recommitReplaces() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/onboarding/plan/commit")
                            .header("Authorization", bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(plan()))
                    .andExpect(status().isOk());
        }
        assertThat(topicRepository.findByUserId(testUserId)).hasSize(3);
    }
}

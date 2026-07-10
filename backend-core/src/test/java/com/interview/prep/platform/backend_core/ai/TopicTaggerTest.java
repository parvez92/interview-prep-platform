package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.ProfileSkills.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopicTaggerTest {

    private final TopicTagger tagger = new TopicTagger(new SkillSurface(new ObjectMapper()));

    /** The profile currently in the database for the plan under review. */
    private static final List<Skill> PROFILE = List.of(
            new Skill("Java", "expert", 11),
            new Skill("Spring Boot", "expert", 8),
            new Skill("AWS", "expert", 7),
            new Skill("Kafka", "advanced", 5),
            new Skill("Kubernetes", "intermediate", 3));

    @Test
    @DisplayName("expert-claimed surface is tagged exp, not new")
    void heapLayoutIsClaimedSurface() {
        String tag = tagger.tag("language", "Heap/stack layout & the generational hypothesis", null, PROFILE);
        assertThat(tag).isIn(TopicTagger.TAG_REFRESH, TopicTagger.TAG_EXP);
    }

    @Test
    @DisplayName("a framework module the résumé names is exp, even under a different skill entry")
    void springIntegrationIsExp() {
        String tag = tagger.tag("framework", "Spring Integration (EIP) — channels, ServiceActivator", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_EXP);
    }

    @Test
    @DisplayName("a Java API postdating the claimed years stays new — expertise in a language is not expertise in every feature")
    void virtualThreadsAreNew() {
        String tag = tagger.tag("language", "Virtual threads & structured concurrency", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_NEW);
    }

    @Test
    @DisplayName("category dsa short-circuits every other rule")
    void dsaCategoryWins() {
        String tag = tagger.tag("dsa", "Binary search trees — AVL vs Red-Black", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_DSA);
    }

    @Test
    @DisplayName("a skill listed below expert level earns refresh, not exp")
    void intermediateSkillIsRefresh() {
        String tag = tagger.tag("cloud", "Kubernetes probes — liveness, readiness, HPA", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_REFRESH);
    }

    @Test
    @DisplayName("an advanced skill still counts as depth")
    void advancedSkillIsExp() {
        String tag = tagger.tag("domain", "Kafka ISR & exactly-once semantics", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_EXP);
    }

    @Test
    @DisplayName("nothing on the résumé matches → new")
    void unmatchedIsNew() {
        String tag = tagger.tag("framework", "GraphQL federation & schema stitching", null, PROFILE);
        assertThat(tag).isEqualTo(TopicTagger.TAG_NEW);
    }

    @Test
    @DisplayName("no profile at all degrades to new rather than throwing")
    void emptyProfile() {
        assertThat(tagger.tag("language", "Heap layout", null, List.of())).isEqualTo(TopicTagger.TAG_NEW);
    }
}

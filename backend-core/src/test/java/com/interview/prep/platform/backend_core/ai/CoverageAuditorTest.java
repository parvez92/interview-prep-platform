package com.interview.prep.platform.backend_core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.prep.platform.backend_core.ai.CoverageAuditor.Gap;
import com.interview.prep.platform.backend_core.ai.CoverageAuditor.SkillCoverage;
import com.interview.prep.platform.backend_core.ai.ProfileSkills.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverageAuditorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CoverageAuditor auditor =
            new CoverageAuditor(new Checklists(mapper), new SkillSurface(mapper));

    private static final List<Skill> JAVA_EXPERT = List.of(new Skill("Java", "expert", 11));

    @Test
    @DisplayName("the plan we shipped leaves most of the java checklist uncovered")
    void reportsRealGaps() {
        // the seven topics currently generated for week 1
        List<String> topics = List.of(
                "G1 vs ZGC vs Shenandoah — internals and when each wins",
                "Heap/stack layout & the generational hypothesis metaspace TLAB",
                "GC tuning flags & OOM diagnosis at scale heap dump -XX",
                "Java Memory Model, locks, and deadlock debugging happens-before volatile synchronized",
                "ThreadPoolExecutor internals — sizing, queueing, rejection",
                "Stream internals and Optional pitfalls",
                "Profiling under load — async-profiler and JFR");

        SkillCoverage java = only(auditor.audit(topics, JAVA_EXPERT));

        assertThat(java.skill()).isEqualTo("java");
        assertThat(java.gaps()).extracting(Gap::id)
                .contains("virtual-threads", "generics", "collections", "graalvm", "class-loading");
        assertThat(java.isReady()).isFalse();
    }

    @Test
    @DisplayName("a checklist only applies to a skill claimed at expert/advanced")
    void skipsSkillsNotClaimedDeeply() {
        List<Skill> beginner = List.of(new Skill("Java", "intermediate", 2));
        assertThat(auditor.audit(List.of("Heap layout"), beginner)).isEmpty();
    }

    @Test
    @DisplayName("covering every item reports 100%")
    void fullCoverage() {
        List<String> topics = List.of(
                "class loading and parent delegation", "heap metaspace generational",
                "G1 ZGC Shenandoah garbage collect", "OOM heap dump -XX GC tuning",
                "JIT tiered C2 escape analysis", "GraalVM native image AOT",
                "memory leak ThreadLocal leak retained heap", "JFR async-profiler profiling",
                "happens-before memory model JMM", "volatile synchronized ReentrantLock",
                "ThreadPoolExecutor ExecutorService thread pool", "CompletableFuture thenCompose",
                "virtual thread Loom carrier thread", "Stream API collector groupingBy",
                "HashMap internals ConcurrentHashMap load factor collision",
                "record sealed pattern matching", "generics type erasure PECS wildcard");

        assertThat(only(auditor.audit(topics, JAVA_EXPERT)).coveragePct()).isEqualTo(100);
    }

    private static SkillCoverage only(List<SkillCoverage> all) {
        return all.stream().filter(c -> c.skill().equals("java")).findFirst().orElseThrow();
    }
}

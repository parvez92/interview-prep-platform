package com.interview.prep.platform.backend_core.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentValidatorTest {

    // ── point specificity: real lines from the reference tracker must pass ──────

    @Test
    void referenceTrackerPoints_pass() {
        assertThat(ContentValidator.pointIsSpecific(
                "G1 (default 9+): region-based, targets pause via -XX:MaxGCPauseMillis.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Young Gen (Eden + S0/S1) → Old Gen via tenuring. Most objects die young.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Spring Boot fat JARs use <code>LaunchedURLClassLoader</code> for nested JARs.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "ZGC: sub-ms pauses regardless of heap size — latency-sensitive APIs.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Bar: Two Sum, Best Time to Buy/Sell Stock, Contains Duplicate, Valid Anagram.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "C2 optimisations: method inlining, escape analysis (stack alloc), loop unrolling.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Use spring.datasource.hikari.maximum-pool-size to bound connections.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Prefer CompletableFuture over raw threads for composition.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "acks=all is the durability floor for produce requests.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Set enable.idempotence to dedupe producer retries per partition.")).isTrue();
        assertThat(ContentValidator.pointIsSpecific(
                "Use read_committed isolation to hide aborted records.")).isTrue();
    }

    @Test
    void vaguePoints_fail() {
        assertThat(ContentValidator.pointIsSpecific("Practice more problems to get better.")).isFalse();
        assertThat(ContentValidator.pointIsSpecific("Understand the basics of garbage collection.")).isFalse();
        assertThat(ContentValidator.pointIsSpecific("Focus on the fundamentals before moving on.")).isFalse();
        assertThat(ContentValidator.pointIsSpecific("")).isFalse();
        assertThat(ContentValidator.pointIsSpecific(null)).isFalse();
    }

    // ── concept ──────────────────────────────────────────────────────────────────

    @Test
    void concept_rules() {
        assertThat(ContentValidator.conceptOk(
                "The JVM loads, links, and runs bytecode. Class loading follows parent-delegation.")).isTrue();
        assertThat(ContentValidator.conceptOk(
                "Learn how the JVM works and understand class loading deeply and thoroughly.")).isFalse();
        assertThat(ContentValidator.conceptOk("Too short.")).isFalse();
        assertThat(ContentValidator.conceptOk(null)).isFalse();
    }

    // ── angle ────────────────────────────────────────────────────────────────────

    @Test
    void angle_rules() {
        assertThat(ContentValidator.angleOk(
                "'50ms P99 SLO, 16GB heap, which GC?' Strong answers name ZGC and justify via pause goals.")).isTrue();
        assertThat(ContentValidator.angleOk(
                "The classic trade-off: throughput vs latency — know when each collector wins.")).isTrue();
        assertThat(ContentValidator.angleOk("This is an important topic for interviews.")).isFalse();
        assertThat(ContentValidator.angleOk(null)).isFalse();
    }

    // ── full topic validation ────────────────────────────────────────────────────

    @Test
    void fullTopic_passesAndFails() {
        Map<String, Object> good = Map.of(
                "concept", "GC reclaims unreachable objects; the core trade-off is throughput vs latency.",
                "points", List.of(
                        "G1 (default since Java 9): region-based, tune via -XX:MaxGCPauseMillis.",
                        "ZGC: sub-millisecond pauses at 100GB+ heaps — pick when P99 < 10ms.",
                        "Escape analysis lets C2 stack-allocate objects that never escape.",
                        "OOM flavors: heap space, Metaspace, GC overhead limit exceeded."),
                "angle", "Which GC for a 50ms P99 SLO and why? Strong answers cite pause-time goals.",
                "est_minutes", 60);
        assertThat(ContentValidator.validateDepthTopic(good)).isEmpty();

        Map<String, Object> bad = Map.of(
                "concept", "Learn GC.",
                "points", List.of("Practice a lot.", "Understand the heap."),
                "angle", "GC is important.",
                "est_minutes", 5);
        List<String> failures = ContentValidator.validateDepthTopic(bad);
        assertThat(failures).hasSize(4);
    }
}

package com.interview.prep.platform.backend_core.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeTermsTest {

    @Test
    @DisplayName("pulls the mechanisms out of a coarse title, dropping the heading")
    void extractsScopeTerms() {
        List<String> terms = ScopeTerms.extract(
                "Java concurrency internals — ThreadPoolExecutor, CompletableFuture, locks", null);

        assertThat(terms).containsExactly("ThreadPoolExecutor", "CompletableFuture", "locks");
    }

    @Test
    @DisplayName("keeps acronyms and short leaf phrases, drops filler")
    void keepsConcreteSegments() {
        assertThat(ScopeTerms.extract("Kafka internals — ISR, exactly-once, log compaction", null))
                .containsExactly("Kafka internals", "ISR", "exactly-once", "log compaction");
    }

    @Test
    @DisplayName("a narrowed split leaves the dropped mechanisms uncovered")
    void detectsNarrowing() {
        List<String> terms = ScopeTerms.contract(
                "Java concurrency internals — ThreadPoolExecutor, CompletableFuture, locks", null);

        List<String> oneChild = List.of(
                "ThreadPoolExecutor internals — sizing, queueing, rejection "
                        + "corePoolSize maximumPoolSize CallerRunsPolicy LinkedBlockingQueue");

        assertThat(ScopeTerms.uncovered(terms, oneChild))
                .containsExactly("CompletableFuture", "locks");
    }

    @Test
    @DisplayName("a genuine 3-way split covers every term")
    void acceptsCoveredSplit() {
        List<String> terms = ScopeTerms.contract(
                "Java concurrency internals — ThreadPoolExecutor, CompletableFuture, locks", null);

        List<String> children = List.of(
                "ThreadPoolExecutor internals — sizing and rejection policies",
                "CompletableFuture composition — thenCompose vs thenApply",
                "Java Memory Model and ReentrantLock — deadlock debugging");

        assertThat(ScopeTerms.uncovered(terms, children)).isEmpty();
    }

    @Test
    @DisplayName("'lock' is covered by ReentrantLock but not by LinkedBlockingQueue")
    void matchesAtWordEdgesOnly() {
        assertThat(ScopeTerms.covers("locks", "uses a LinkedBlockingQueue")).isFalse();
        assertThat(ScopeTerms.covers("locks", "ReentrantLock and StampedLock")).isTrue();
    }

    @Test
    @DisplayName("a title that enumerates nothing obliges nothing — only its scope line does")
    void nonEnumeratingTitleIsNotAContract() {
        assertThat(ScopeTerms.contract("kafka-internals", "partitions, ISR, exactly-once"))
                .containsExactly("partitions", "ISR", "exactly-once");

        // …but it still describes the topic, so tagging can see it
        assertThat(ScopeTerms.extract("kafka-internals", null)).containsExactly("kafka-internals");
    }

    @Test
    @DisplayName("coverage tolerates plural/singular drift")
    void pluralTolerance() {
        assertThat(ScopeTerms.covers("locks", "ReentrantLock semantics")).isTrue();
        assertThat(ScopeTerms.covers("generics", "Generic type erasure")).isTrue();
        assertThat(ScopeTerms.covers("CompletableFuture", "ThreadPoolExecutor sizing")).isFalse();
    }
}

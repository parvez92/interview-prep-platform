package com.interview.prep.platform.backend_core.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSurfaceTest {

    @Test
    @DisplayName("a singular checklist term matches the plural the plan actually wrote")
    void pluralTolerance() {
        assertThat(SkillSurface.containsTerm("virtual threads (java 21) & structured concurrency", "virtual thread")).isTrue();
        assertThat(SkillSurface.containsTerm("carrier threads and mounting", "carrier thread")).isTrue();
        assertThat(SkillSurface.containsTerm("lazy stream pipelines and collectors", "collector")).isTrue();
        assertThat(SkillSurface.containsTerm("records for dtos", "record")).isTrue();
    }

    @Test
    @DisplayName("a plural term still matches the singular")
    void reversePluralTolerance() {
        assertThat(SkillSurface.containsTerm("generic type erasure", "generics")).isTrue();
        assertThat(SkillSurface.containsTerm("generics and wildcards", "generics")).isTrue();
    }

    @Test
    @DisplayName("word boundaries still reject terms hiding inside other words")
    void boundariesHold() {
        assertThat(SkillSurface.containsTerm("piqued my interest", "rest")).isFalse();
        assertThat(SkillSurface.containsTerm("g10 instance sizing", "g1")).isFalse();
        assertThat(SkillSurface.containsTerm("a clash of names", "class")).isFalse();
        assertThat(SkillSurface.containsTerm("recorder pattern", "record")).isFalse();
    }

    @Test
    @DisplayName("symbol-anchored terms match without word boundaries")
    void symbolTerms() {
        assertThat(SkillSurface.containsTerm("-xx:maxgcpausemillis=50", "-XX")).isTrue();
        assertThat(SkillSurface.containsTerm("the n+1 problem", "n+1")).isTrue();
        assertThat(SkillSurface.containsTerm("hits /actuator/health", "/actuator")).isTrue();
        assertThat(SkillSurface.containsTerm("@cacheable on the service", "@Cacheable")).isTrue();
    }
}

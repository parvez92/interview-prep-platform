package com.interview.prep.platform.backend_core.study;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessCalculatorTest {

    private final ReadinessCalculator calc = new ReadinessCalculator();

    private Topic topic(String status, Integer confidence) {
        Topic t = new Topic();
        t.setStatus(status);
        t.setConfidence(confidence);
        return t;
    }

    @Test
    void emptyList_returnsZero() {
        assertThat(calc.calculate(List.of(), 0)).isEqualTo(0);
    }

    @Test
    void allDoneNoFlagsFullConfidence_returns100() {
        List<Topic> topics = List.of(topic("done", 100), topic("done", 100), topic("done", 100));
        assertThat(calc.calculate(topics, 0)).isEqualTo(100);
    }

    @Test
    void noDoneTopics_returnsZero() {
        List<Topic> topics = List.of(topic("todo", 80), topic("todo", 80));
        assertThat(calc.calculate(topics, 0)).isEqualTo(0);
    }

    @Test
    void halfDone_lowerThanAllDone() {
        List<Topic> all = List.of(topic("done", 100), topic("done", 100));
        List<Topic> half = List.of(topic("done", 100), topic("todo", 100));
        assertThat(calc.calculate(half, 0)).isLessThan(calc.calculate(all, 0));
    }

    @Test
    void openFlagsReduceScore() {
        List<Topic> topics = List.of(topic("done", 100), topic("done", 100));
        int noFlags = calc.calculate(topics, 0);
        int withFlags = calc.calculate(topics, 1);
        assertThat(withFlags).isLessThan(noFlags);
    }

    @Test
    void scoreNeverExceeds100() {
        List<Topic> topics = IntStream.range(0, 5)
                .mapToObj(i -> topic("done", 100))
                .toList();
        assertThat(calc.calculate(topics, 0)).isLessThanOrEqualTo(100);
    }

    @Test
    void scoreNeverNegative() {
        List<Topic> topics = List.of(topic("todo", 0));
        assertThat(calc.calculate(topics, 100)).isGreaterThanOrEqualTo(0);
    }
}

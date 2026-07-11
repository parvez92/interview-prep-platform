package com.interview.prep.platform.backend_core.study.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Add a topic to an existing week. The week is addressed by {@code weekCode} (the UI's
 * custom-topic flow) or {@code weekNumber} (the global 1..N week, used by plan amendments);
 * the service requires exactly one.
 *
 * <p>{@code source} and {@code priority} are optional — omit them and a user-added topic
 * defaults to {@code custom}/{@code high}. Plan amendments set them explicitly. {@code scope}
 * maps onto the topic's angle (the depth pass's coverage contract).
 */
public record CreateTopicDto(
        String weekCode,
        Integer weekNumber,
        @NotBlank String title,
        @NotBlank @Pattern(regexp = "new|refresh|dsa|exp") String tag,
        @Pattern(regexp = "resume|standard|interest|custom") String source,
        @Pattern(regexp = "high|medium|low") String priority,
        String concept,
        List<String> points,
        String angle,
        String scope,
        String splitHint
) {}

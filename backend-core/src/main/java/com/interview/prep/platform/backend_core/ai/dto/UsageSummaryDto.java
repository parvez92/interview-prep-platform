package com.interview.prep.platform.backend_core.ai.dto;

import java.math.BigDecimal;

public record UsageSummaryDto(
        BigDecimal monthUsd,
        BigDecimal budgetUsd,
        BigDecimal remainingUsd,
        boolean warning
) {}

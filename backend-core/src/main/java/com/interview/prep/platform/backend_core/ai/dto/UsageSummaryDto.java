package com.interview.prep.platform.backend_core.ai.dto;

import java.math.BigDecimal;

public record UsageSummaryDto(BigDecimal monthCostUsd, BigDecimal budgetUsd, int totalCalls) {}

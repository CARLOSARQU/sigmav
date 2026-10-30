package com.sigmav.sigmavbackend.modules.simulacro.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Career-level cut-off comparison after completing a simulacro.
 * Shows per-career minimum scores and whether the student's score passes.
 */
public record CareerThresholdResponse(
        Long areaId,
        String areaName,
        String universityName,
        List<CareerThreshold> careers
) {
    public record CareerThreshold(
            Long careerId,
            String careerName,
            BigDecimal minimumScore,
            double studentScore,
            boolean passed
    ) {}
}

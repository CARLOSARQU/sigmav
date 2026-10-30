package com.sigmav.sigmavbackend.modules.simulacro.dto;

import java.math.BigDecimal;

public record CareerResponse(
        Long id,
        String name,
        String areaName,
        String universityName,
        BigDecimal minimumScore
) {}

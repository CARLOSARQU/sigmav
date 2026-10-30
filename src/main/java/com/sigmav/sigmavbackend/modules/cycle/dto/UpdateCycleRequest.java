package com.sigmav.sigmavbackend.modules.cycle.dto;

import java.time.LocalDate;

public record UpdateCycleRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive
) {}

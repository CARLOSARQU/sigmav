package com.sigmav.sigmavbackend.modules.cycle.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CycleResponse(
        Long id,
        UUID uuid,
        Long academyId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isActive,
        LocalDateTime createdAt
) {}

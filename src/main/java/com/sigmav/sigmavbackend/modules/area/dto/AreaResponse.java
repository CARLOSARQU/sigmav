package com.sigmav.sigmavbackend.modules.area.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AreaResponse(
        Long id,
        UUID uuid,
        Long academyId,
        Long cycleId,
        String name,
        String description,
        Boolean isActive,
        LocalDateTime createdAt
) {}

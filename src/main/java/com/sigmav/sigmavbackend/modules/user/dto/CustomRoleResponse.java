package com.sigmav.sigmavbackend.modules.user.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CustomRoleResponse(
        Long id,
        UUID uuid,
        Long academyId,
        String name,
        String description,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> permissions
) {}

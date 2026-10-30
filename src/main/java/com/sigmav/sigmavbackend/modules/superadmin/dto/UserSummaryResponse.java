package com.sigmav.sigmavbackend.modules.superadmin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserSummaryResponse(
        Long id,
        UUID uuid,
        Long academyId,
        String academyName,   // null si es SUPER_ADMIN
        String email,
        String fullName,
        String role,
        boolean isActive,
        LocalDateTime createdAt
) {}

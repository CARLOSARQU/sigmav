package com.sigmav.sigmavbackend.modules.guardian.dto;

import com.sigmav.sigmavbackend.modules.guardian.entity.RelationshipType;

import java.time.LocalDateTime;
import java.util.UUID;

public record GuardianResponse(
        Long id,
        UUID uuid,
        Long studentId,
        String fullName,
        String phone,
        String email,
        RelationshipType relationship,
        Boolean isActive,
        LocalDateTime createdAt,
        Boolean sendReports
) {}

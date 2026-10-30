package com.sigmav.sigmavbackend.modules.academy.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AcademyResponse(
        Long id,
        UUID uuid,
        String name,
        String subdomain,
        String planType,
        Integer maxStudents,
        Integer maxCourses,
        Integer maxStorageMb,
        Map<String, Boolean> features,
        Boolean isActive,
        LocalDateTime subscriptionStart,
        LocalDateTime subscriptionEnd,
        LocalDateTime createdAt
) {}

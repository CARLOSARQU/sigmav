package com.sigmav.sigmavbackend.modules.course.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CourseResponse(
        Long id,
        UUID uuid,
        Long academyId,
        Long areaId,
        String name,
        String description,
        Boolean isActive,
        LocalDateTime createdAt
) {}

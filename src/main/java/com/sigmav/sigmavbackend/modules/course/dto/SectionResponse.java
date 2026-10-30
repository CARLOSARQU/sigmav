package com.sigmav.sigmavbackend.modules.course.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SectionResponse(
        Long id,
        UUID uuid,
        Long courseId,
        String title,
        Integer orderIndex,
        Boolean isActive,
        LocalDateTime createdAt
) {}

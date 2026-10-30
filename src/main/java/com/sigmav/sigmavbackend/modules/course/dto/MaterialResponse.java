package com.sigmav.sigmavbackend.modules.course.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialResponse(
        Long id,
        UUID uuid,
        Long courseId,
        Long sectionId,
        String title,
        String materialType,
        String fileUrl,
        Integer orderIndex,
        Boolean isActive,
        LocalDateTime createdAt
) {}

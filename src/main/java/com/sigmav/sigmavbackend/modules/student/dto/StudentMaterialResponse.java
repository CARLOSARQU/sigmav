package com.sigmav.sigmavbackend.modules.student.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** MaterialResponse extendido para el portal del estudiante — incluye progreso. */
public record StudentMaterialResponse(
        Long id,
        UUID uuid,
        Long courseId,
        Long sectionId,
        String title,
        String materialType,
        String fileUrl,
        Integer orderIndex,
        Boolean isActive,
        LocalDateTime createdAt,
        Boolean viewed
) {}

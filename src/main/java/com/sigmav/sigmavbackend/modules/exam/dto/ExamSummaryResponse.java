package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Versión ligera sin preguntas, para listados */
public record ExamSummaryResponse(
        Long id,
        UUID uuid,
        String scopeType,
        Long cycleId,
        Long areaId,
        Long courseId,
        String title,
        String status,
        LocalTime availableFrom,
        LocalTime availableUntil,
        Integer durationMinutes,
        Integer maxAttempts,
        BigDecimal passingScore,
        LocalDateTime createdAt
) {}

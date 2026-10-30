package com.sigmav.sigmavbackend.modules.simulacro.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/** Versión de ExamSummaryResponse con información de área, para listados de simulacros. */
public record SimulacroSummaryResponse(
        Long id,
        UUID uuid,
        String scopeType,
        String title,
        String status,
        LocalTime availableFrom,
        LocalTime availableUntil,
        Integer durationMinutes,
        Integer maxAttempts,
        BigDecimal passingScore,
        LocalDateTime createdAt,
        Long areaId,
        String areaName
) {}

package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ExamResponse(
        Long id,
        UUID uuid,
        String scopeType,
        Long cycleId,
        Long areaId,
        Long courseId,
        Long createdBy,
        String title,
        String description,
        String status,
        LocalDate scheduledDate,
        LocalTime availableFrom,
        LocalTime availableUntil,
        Integer durationMinutes,
        Integer maxAttempts,
        BigDecimal passingScore,
        Boolean isPublished,
        List<QuestionResponse> questions,
        LocalDateTime createdAt
) {}

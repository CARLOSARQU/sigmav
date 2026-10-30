package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateExamRequest(
        String title,
        String description,
        LocalDate scheduledDate,
        LocalTime availableFrom,
        LocalTime availableUntil,
        Integer durationMinutes,
        Integer maxAttempts,
        BigDecimal passingScore
) {}

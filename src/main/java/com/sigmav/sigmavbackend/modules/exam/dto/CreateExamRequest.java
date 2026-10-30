package com.sigmav.sigmavbackend.modules.exam.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateExamRequest(
        @NotBlank(message = "El título es requerido")
        String title,

        String description,

        /** CYCLE | AREA | COURSE */
        @NotBlank(message = "El tipo de alcance es requerido")
        String scopeType,

        Long cycleId,
        Long areaId,
        Long courseId,

        LocalDate scheduledDate,

        LocalTime availableFrom,
        LocalTime availableUntil,

        @Min(value = 1, message = "La duración debe ser al menos 1 minuto")
        Integer durationMinutes,

        @Min(value = 1, message = "Debe permitirse al menos 1 intento")
        Integer maxAttempts,

        @DecimalMin(value = "0.0", message = "El puntaje mínimo de aprobación debe ser >= 0")
        BigDecimal passingScore
) {}

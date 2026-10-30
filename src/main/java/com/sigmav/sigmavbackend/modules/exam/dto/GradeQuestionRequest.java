package com.sigmav.sigmavbackend.modules.exam.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GradeQuestionRequest(
        @NotNull(message = "El ID de la pregunta es requerido")
        Long questionId,

        @NotNull(message = "El puntaje es requerido")
        BigDecimal score,

        String feedback
) {}

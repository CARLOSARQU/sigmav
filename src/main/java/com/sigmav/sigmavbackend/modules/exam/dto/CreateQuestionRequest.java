package com.sigmav.sigmavbackend.modules.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateQuestionRequest(
        @NotBlank(message = "El enunciado es requerido")
        String questionText,

        /** SINGLE_CHOICE | MULTIPLE_CHOICE | TEXT | FILE_UPLOAD */
        @NotBlank(message = "El tipo de pregunta es requerido")
        String questionType,

        List<String> options,          // para SINGLE_CHOICE y MULTIPLE_CHOICE
        List<String> correctAnswer,    // para SINGLE_CHOICE y MULTIPLE_CHOICE

        @NotNull(message = "Los puntos son requeridos")
        BigDecimal points,

        Integer orderIndex
) {}

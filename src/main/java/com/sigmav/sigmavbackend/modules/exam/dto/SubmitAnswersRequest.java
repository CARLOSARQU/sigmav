package com.sigmav.sigmavbackend.modules.exam.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitAnswersRequest(
        /** Mapa questionId → respuesta del estudiante.
         *  Para SINGLE_CHOICE: "París"
         *  Para MULTIPLE_CHOICE: ["París", "Roma"]
         *  Para TEXT: "texto libre" */
        @NotNull(message = "Las respuestas son requeridas")
        Map<String, Object> answers
) {}

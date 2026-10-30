package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StudentResultResponse(
        Long attemptId,
        UUID attemptUuid,
        Long examId,
        String examTitle,
        Integer attemptNumber,
        String status,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        BigDecimal totalScore,
        BigDecimal examMaxScore,
        BigDecimal passingScore,
        Boolean passed,
        /** Solo visible si status = GRADED */
        Map<String, Object> answers,
        /** Retroalimentación por pregunta, solo si status = GRADED y hay preguntas manuales */
        List<QuestionFeedback> questionFeedback
) {
    public record QuestionFeedback(
            Long questionId,
            String questionText,
            BigDecimal score,
            BigDecimal maxPoints,
            String feedback   // null si el docente no dejó observación
    ) {}
}

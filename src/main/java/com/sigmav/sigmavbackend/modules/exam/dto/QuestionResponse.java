package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionResponse(
        Long id,
        UUID uuid,
        Long examId,
        String questionText,
        String questionType,
        List<String> options,
        List<String> correctAnswer,   // null si requiresManualGrading=true
        BigDecimal points,
        Integer orderIndex,
        Boolean requiresManualGrading
) {}

package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.util.List;

public record UpdateQuestionRequest(
        String questionText,
        List<String> options,
        List<String> correctAnswer,
        BigDecimal points,
        Integer orderIndex
) {}

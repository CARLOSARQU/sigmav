package com.sigmav.sigmavbackend.modules.exam.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record GradeAllRequest(
        @NotNull
        List<QuestionGrade> grades
) {
    public record QuestionGrade(
            @NotNull Long questionId,
            @NotNull BigDecimal score,
            String feedback
    ) {}
}

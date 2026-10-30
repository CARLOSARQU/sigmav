package com.sigmav.sigmavbackend.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExamGradesReportResponse(
        Long examId,
        String examTitle,
        String scopeType,       // COURSE | AREA | CYCLE
        String scopeName,       // nombre del curso / área / ciclo
        String teacherName,     // quien creó el examen
        BigDecimal passingScore,
        int totalAttempts,
        int passedCount,
        int failedCount,
        List<AttemptItem> grades
) {
    public record AttemptItem(
            int numero,
            Long studentId,
            String dni,
            String studentName,
            BigDecimal score,
            String result,      // APROBADO | DESAPROBADO | PENDIENTE | EN_CURSO
            int focusLostCount
    ) {}
}

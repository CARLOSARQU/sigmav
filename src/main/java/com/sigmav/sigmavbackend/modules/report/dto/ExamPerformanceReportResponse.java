package com.sigmav.sigmavbackend.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExamPerformanceReportResponse(
        Long examId,
        String examTitle,
        int totalAttempts,
        BigDecimal averageScore,
        BigDecimal maxScore,
        BigDecimal minScore,
        int passedCount,
        int failedCount,
        double passRate,
        List<ScoreRange> distribution,
        List<TopStudent> top10
) {
    public record ScoreRange(
            String range,
            int count
    ) {}

    public record TopStudent(
            Long studentId,
            String studentFullName,
            BigDecimal score,
            boolean passed,
            int focusLostCount
    ) {}
}

package com.sigmav.sigmavbackend.modules.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record GradebookResponse(
        Long courseId,
        String courseName,
        Long areaId,
        String areaName,
        List<GradebookExam> exams,
        List<GradebookStudent> students
) {
    public record GradebookExam(Long id, String title, BigDecimal passingScore, BigDecimal maxScore) {}

    public record GradebookStudent(Long studentId, String studentName, String dni, List<GradebookScore> scores) {}

    public record GradebookScore(Long examId, BigDecimal score, Boolean passed, String status) {}
}

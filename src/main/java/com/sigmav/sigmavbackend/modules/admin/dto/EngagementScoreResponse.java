package com.sigmav.sigmavbackend.modules.admin.dto;

import java.util.List;

public record EngagementScoreResponse(
        List<StudentEngagementScore> students,
        double academyAvgScore
) {
    public record StudentEngagementScore(
            Long studentId,
            String fullName,
            String email,
            int score,
            double attendanceRate,
            double materialProgress,
            double examCompletion
    ) {}
}

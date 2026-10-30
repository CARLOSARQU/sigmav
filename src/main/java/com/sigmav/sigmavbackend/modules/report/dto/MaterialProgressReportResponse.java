package com.sigmav.sigmavbackend.modules.report.dto;

import java.util.List;

public record MaterialProgressReportResponse(
        Long courseId,
        String courseName,
        int totalEnrolledStudents,
        List<Item> materials
) {
    public record Item(
            Long materialId,
            String materialTitle,
            String materialType,
            int viewedCount,
            double viewRate
    ) {}
}

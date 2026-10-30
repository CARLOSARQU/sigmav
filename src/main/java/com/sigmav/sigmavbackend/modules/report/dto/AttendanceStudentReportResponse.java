package com.sigmav.sigmavbackend.modules.report.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AttendanceStudentReportResponse(
        Long studentId,
        String studentFullName,
        Long courseId,
        String courseName,
        int totalSessions,
        long presentCount,
        long lateCount,
        long absentCount,
        List<Item> sessions
) {
    public record Item(
            Long sessionId,
            String sessionTitle,
            LocalDate sessionDate,
            String status,
            LocalDateTime checkInAt
    ) {}
}

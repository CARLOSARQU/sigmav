package com.sigmav.sigmavbackend.modules.report.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AttendanceSessionReportResponse(
        Long sessionId,
        String sessionTitle,
        LocalDate sessionDate,
        int totalStudents,
        long presentCount,
        long lateCount,
        long absentCount,
        List<Item> attendance
) {
    public record Item(
            Long studentId,
            String studentFullName,
            String status,
            LocalDateTime checkInAt,
            LocalDateTime checkOutAt
    ) {}
}

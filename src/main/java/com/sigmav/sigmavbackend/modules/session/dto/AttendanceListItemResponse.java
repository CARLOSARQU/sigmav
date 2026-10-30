package com.sigmav.sigmavbackend.modules.session.dto;

import java.time.LocalDateTime;

public record AttendanceListItemResponse(
        Long studentId,
        String studentFullName,
        String status,          // PRESENT | LATE | ABSENT
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt,
        String method           // QR | BUTTON | MANUAL | null si ABSENT
) {}

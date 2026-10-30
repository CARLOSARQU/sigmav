package com.sigmav.sigmavbackend.modules.session.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record SessionResponse(
        Long id,
        UUID uuid,
        Long courseId,
        Long teacherId,
        String title,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String attendanceMode,
        String qrToken,
        LocalDateTime attendanceOpenFrom,
        LocalDateTime attendanceOpenUntil,
        Integer attendanceTolerance,
        Boolean isActive,
        LocalDateTime createdAt,
        String meetingUrl
) {}

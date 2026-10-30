package com.sigmav.sigmavbackend.modules.session.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record UpdateSessionRequest(
        String title,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String attendanceMode,
        LocalDateTime attendanceOpenFrom,
        LocalDateTime attendanceOpenUntil,
        Integer attendanceTolerance,
        Boolean isActive,
        String meetingUrl
) {}

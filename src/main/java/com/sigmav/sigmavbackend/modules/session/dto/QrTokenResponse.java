package com.sigmav.sigmavbackend.modules.session.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record QrTokenResponse(
        Long sessionId,
        String qrToken,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime attendanceOpenFrom,
        LocalDateTime attendanceOpenUntil
) {}

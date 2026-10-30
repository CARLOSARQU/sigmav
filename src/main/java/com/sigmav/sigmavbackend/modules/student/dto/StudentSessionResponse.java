package com.sigmav.sigmavbackend.modules.student.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Vista de una sesión de clase para el portal del estudiante.
 * Incluye el estado de asistencia del propio estudiante.
 */
public record StudentSessionResponse(
        Long sessionId,
        Long courseId,
        String courseName,
        String title,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String attendanceMode,
        LocalDateTime attendanceOpenFrom,
        LocalDateTime attendanceOpenUntil,
        Boolean attended,
        String attendanceStatus,   // PRESENT | LATE | ABSENT | null (si no hay registro)
        String meetingUrl
) {}

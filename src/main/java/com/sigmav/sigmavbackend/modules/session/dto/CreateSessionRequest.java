package com.sigmav.sigmavbackend.modules.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CreateSessionRequest(
        @NotBlank(message = "El título es requerido")
        String title,

        @NotNull(message = "La fecha de sesión es requerida")
        LocalDate sessionDate,

        @NotNull(message = "La hora de inicio es requerida")
        LocalTime startTime,

        @NotNull(message = "La hora de fin es requerida")
        LocalTime endTime,

        /** NONE | BUTTON | QR — default NONE */
        String attendanceMode,

        LocalDateTime attendanceOpenFrom,
        LocalDateTime attendanceOpenUntil,

        /** Minutos de tolerancia para tardanza. null = usa el default de la academia */
        Integer attendanceTolerance,

        /** Solo para ACADEMY_ADMIN: ID del docente propietario de la sesión */
        Long teacherId,

        String meetingUrl
) {}

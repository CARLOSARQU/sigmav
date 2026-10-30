package com.sigmav.sigmavbackend.modules.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AttemptResponse(
        Long id,
        UUID uuid,
        Long examId,
        Long studentId,
        Integer attemptNumber,
        String status,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal totalScore,
        Boolean passed,
        Long remainingSeconds,    // null si no IN_PROGRESS o Redis expiró
        Integer focusLostCount,   // salidas de pantalla registradas
        Map<String, Object> answers, // null para intentos activos, poblado para calificación
        String studentFullName,   // solo en getPendingAttempts, null en otros contextos
        String studentDni         // solo en getPendingAttempts, null en otros contextos
) {}

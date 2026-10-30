package com.sigmav.sigmavbackend.modules.student.dto;

import com.sigmav.sigmavbackend.modules.guardian.dto.GuardianResponse;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record StudentResponse(
        Long id,
        UUID uuid,
        Long academyId,
        Long userId,
        String firstName,
        String lastName,
        String fullName,
        String dni,
        String email,
        String phone,
        String code,
        String avatarUrl,
        Boolean isActive,
        LocalDateTime createdAt,
        Boolean hasActiveEnrollment,
        /** Solo se incluye en la respuesta de creación cuando la contraseña fue autogenerada. */
        String generatedPassword,
        /** Apoderados del estudiante. Siempre non-null; vacío si no tiene. */
        List<GuardianResponse> guardians
) {
    public StudentResponse {
        if (guardians == null) guardians = Collections.emptyList();
    }
}

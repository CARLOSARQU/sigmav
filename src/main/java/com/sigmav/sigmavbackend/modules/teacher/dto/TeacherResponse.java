package com.sigmav.sigmavbackend.modules.teacher.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TeacherResponse(
        Long id,
        UUID uuid,
        String fullName,
        String dni,
        String email,
        String avatarUrl,
        Boolean isActive,
        LocalDateTime createdAt,
        /** Solo se incluye en la respuesta de creación cuando la contraseña fue autogenerada. */
        String generatedPassword
) {}

package com.sigmav.sigmavbackend.modules.teacher.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateTeacherRequest(
        @NotBlank(message = "El nombre completo es requerido")
        String fullName,

        String dni,

        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        String email,

        /** Contraseña inicial. Si es null o vacía, se genera automáticamente. */
        String password
) {}

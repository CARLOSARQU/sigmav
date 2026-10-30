package com.sigmav.sigmavbackend.modules.superadmin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAcademyUserRequest(
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "La contraseña es requerida")
        String password,

        @NotBlank(message = "El nombre completo es requerido")
        String fullName,

        // ACADEMY_ADMIN o TEACHER (por defecto ACADEMY_ADMIN si no se especifica)
        String role
) {}

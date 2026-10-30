package com.sigmav.sigmavbackend.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        String email,

        @NotBlank(message = "La contraseña es requerida")
        String password,

        // Código de academia (subdomain). Requerido para todos excepto SUPER_ADMIN.
        String subdomain
) {}

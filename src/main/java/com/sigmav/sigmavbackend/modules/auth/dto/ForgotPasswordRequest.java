package com.sigmav.sigmavbackend.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email inválido")
        String email,

        String subdomain
) {}

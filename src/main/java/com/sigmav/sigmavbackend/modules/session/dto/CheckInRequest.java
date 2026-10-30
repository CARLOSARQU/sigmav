package com.sigmav.sigmavbackend.modules.session.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(
        @NotBlank(message = "El token QR es requerido")
        String qrToken
) {}

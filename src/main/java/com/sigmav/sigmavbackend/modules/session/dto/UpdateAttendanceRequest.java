package com.sigmav.sigmavbackend.modules.session.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAttendanceRequest(
        /** PRESENT | LATE | ABSENT */
        @NotBlank(message = "El estado es requerido")
        String status
) {}

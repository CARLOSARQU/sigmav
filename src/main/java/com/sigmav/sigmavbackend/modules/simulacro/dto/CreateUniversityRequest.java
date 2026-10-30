package com.sigmav.sigmavbackend.modules.simulacro.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUniversityRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,

        String description
) {}

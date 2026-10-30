package com.sigmav.sigmavbackend.modules.simulacro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAreaRequest(
        @NotNull(message = "El ID de la universidad es requerido")
        Long universityId,

        @NotBlank(message = "El nombre es requerido")
        String name,

        String description
) {}

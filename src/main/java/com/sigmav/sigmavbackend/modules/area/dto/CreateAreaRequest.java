package com.sigmav.sigmavbackend.modules.area.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAreaRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,
        String description
) {}

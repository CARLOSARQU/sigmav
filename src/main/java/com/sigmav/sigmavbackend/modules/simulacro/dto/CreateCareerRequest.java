package com.sigmav.sigmavbackend.modules.simulacro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCareerRequest(
        @NotNull(message = "El ID del área es requerido")
        Long areaId,

        @NotBlank(message = "El nombre es requerido")
        String name,

        String description,

        BigDecimal minimumScore
) {}

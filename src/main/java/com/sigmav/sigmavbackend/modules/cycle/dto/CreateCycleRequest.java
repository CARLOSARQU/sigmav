package com.sigmav.sigmavbackend.modules.cycle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCycleRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,

        @NotNull(message = "La fecha de inicio es requerida")
        LocalDate startDate,

        @NotNull(message = "La fecha de fin es requerida")
        LocalDate endDate
) {}

package com.sigmav.sigmavbackend.modules.course.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSectionRequest(
        @NotBlank(message = "El título es requerido")
        String title,
        Integer orderIndex
) {}

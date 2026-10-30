package com.sigmav.sigmavbackend.modules.course.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,
        String description
) {}

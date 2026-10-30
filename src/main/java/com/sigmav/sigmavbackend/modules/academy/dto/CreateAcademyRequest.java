package com.sigmav.sigmavbackend.modules.academy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateAcademyRequest(
        @NotBlank(message = "El nombre es requerido")
        String name,

        @NotBlank(message = "El subdominio es requerido")
        String subdomain,

        String planType,

        @Positive(message = "Max estudiantes debe ser positivo")
        Integer maxStudents,

        @Positive(message = "Max cursos debe ser positivo")
        Integer maxCourses,

        @Positive(message = "Max almacenamiento debe ser positivo")
        Integer maxStorageMb,

        // Campos opcionales para crear el ACADEMY_ADMIN al mismo tiempo
        @Email(message = "Email del admin inválido")
        String adminEmail,

        String adminPassword,

        String adminFullName
) {}

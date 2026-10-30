package com.sigmav.sigmavbackend.modules.guardian.dto;

import com.sigmav.sigmavbackend.modules.guardian.entity.RelationshipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGuardianRequest(
        @NotBlank(message = "El nombre del apoderado es requerido")
        String fullName,

        String phone,

        @Email(message = "Email inválido")
        String email,

        @NotNull(message = "El parentesco es requerido")
        RelationshipType relationship,

        /** Si es null, se interpreta como true (enviar reportes). */
        Boolean sendReports
) {}

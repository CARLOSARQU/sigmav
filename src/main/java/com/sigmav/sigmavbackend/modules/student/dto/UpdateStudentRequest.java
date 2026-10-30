package com.sigmav.sigmavbackend.modules.student.dto;

import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateStudentRequest(
        String firstName,
        String lastName,
        String dni,
        String email,
        @Size(max = 9, message = "El teléfono no puede superar 9 dígitos")
        String phone,
        String code,

        /** Apoderados. Null = no tocar, vacío = limpiar, poblado = reemplazar. */
        List<CreateGuardianRequest> guardians
) {}

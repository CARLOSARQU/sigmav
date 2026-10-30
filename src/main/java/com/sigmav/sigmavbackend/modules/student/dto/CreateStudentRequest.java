package com.sigmav.sigmavbackend.modules.student.dto;

import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateStudentRequest(
        @NotBlank(message = "El nombre es requerido")
        String firstName,

        @NotBlank(message = "El apellido es requerido")
        String lastName,

        @NotBlank(message = "El DNI es requerido")
        String dni,

        @Email(message = "Email inválido")
        String email,

        @Size(max = 9, message = "El teléfono no puede superar 9 dígitos")
        String phone,

        String code,

        /** Contraseña inicial. Si es null o vacía, se genera automáticamente. */
        String password,

        /** Apoderados. Null = no se procesan guardianes en creación. */
        List<CreateGuardianRequest> guardians
) {}

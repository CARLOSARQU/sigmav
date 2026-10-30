package com.sigmav.sigmavbackend.modules.superadmin.dto;

public record UpdateUserRequest(
        String role,       // nullable — cambiar rol
        Boolean isActive,   // nullable — activar/desactivar
        Long academyId
) {}

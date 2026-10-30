package com.sigmav.sigmavbackend.modules.permission.dto;

import jakarta.validation.constraints.NotBlank;

public record PermissionOverrideRequest(
        @NotBlank String permissionName,
        boolean granted
) {}

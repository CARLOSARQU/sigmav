package com.sigmav.sigmavbackend.modules.permission.dto;

import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull String role) {}

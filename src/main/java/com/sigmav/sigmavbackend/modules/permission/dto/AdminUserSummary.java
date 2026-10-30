package com.sigmav.sigmavbackend.modules.permission.dto;

public record AdminUserSummary(
        Long id,
        String fullName,
        String email,
        String role,
        boolean isActive
) {}

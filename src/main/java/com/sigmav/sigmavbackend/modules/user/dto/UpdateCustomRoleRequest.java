package com.sigmav.sigmavbackend.modules.user.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateCustomRoleRequest(
        @Size(max = 60) String name,
        @Size(max = 1000) String description,
        Boolean isActive,
        List<String> permissions
) {}

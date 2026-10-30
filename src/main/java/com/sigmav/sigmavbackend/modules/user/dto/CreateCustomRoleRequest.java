package com.sigmav.sigmavbackend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCustomRoleRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 1000) String description,
        List<String> permissions
) {}

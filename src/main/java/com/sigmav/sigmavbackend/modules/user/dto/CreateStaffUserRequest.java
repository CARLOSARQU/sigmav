package com.sigmav.sigmavbackend.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStaffUserRequest(
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @NotNull String role,
        String password,
        String phone
) {}

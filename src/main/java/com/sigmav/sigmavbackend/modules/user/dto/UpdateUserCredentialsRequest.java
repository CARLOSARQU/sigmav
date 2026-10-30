package com.sigmav.sigmavbackend.modules.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserCredentialsRequest(
        @Email String email,
        String password
) {}

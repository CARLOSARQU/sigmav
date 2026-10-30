package com.sigmav.sigmavbackend.modules.user.dto;

public record StaffUserCreatedResponse(
        Long id,
        String email,
        String fullName,
        String role,
        String generatedPassword,
        String phone
) {}

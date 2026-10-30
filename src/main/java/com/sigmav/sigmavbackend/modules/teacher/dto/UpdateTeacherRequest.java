package com.sigmav.sigmavbackend.modules.teacher.dto;

public record UpdateTeacherRequest(
        String fullName,
        String dni,
        String email,
        String password
) {}

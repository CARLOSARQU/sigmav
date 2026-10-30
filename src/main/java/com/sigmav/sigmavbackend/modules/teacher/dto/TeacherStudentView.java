package com.sigmav.sigmavbackend.modules.teacher.dto;

public record TeacherStudentView(
        Long studentId,
        String fullName,
        String dni,
        String email,
        String phone,
        Boolean isActive
) {}

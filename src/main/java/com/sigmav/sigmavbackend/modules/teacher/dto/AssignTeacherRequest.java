package com.sigmav.sigmavbackend.modules.teacher.dto;

public record AssignTeacherRequest(
        Boolean canCreateExams   // nullable, default false
) {}

package com.sigmav.sigmavbackend.modules.course.dto;

public record UpdateCourseRequest(
        String name,
        String description,
        Boolean isActive
) {}

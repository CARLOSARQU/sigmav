package com.sigmav.sigmavbackend.modules.course.dto;

public record UpdateSectionRequest(
        String title,
        Integer orderIndex,
        Boolean isActive
) {}

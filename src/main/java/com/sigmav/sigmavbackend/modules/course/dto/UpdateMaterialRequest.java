package com.sigmav.sigmavbackend.modules.course.dto;

public record UpdateMaterialRequest(
        String title,
        String fileUrl,
        Integer orderIndex,
        Boolean isActive
) {}

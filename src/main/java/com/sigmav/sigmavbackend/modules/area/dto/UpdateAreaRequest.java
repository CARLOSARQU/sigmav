package com.sigmav.sigmavbackend.modules.area.dto;

public record UpdateAreaRequest(
        String name,
        String description,
        Boolean isActive
) {}

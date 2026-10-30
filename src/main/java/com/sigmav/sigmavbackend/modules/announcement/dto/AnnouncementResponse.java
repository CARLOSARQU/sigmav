package com.sigmav.sigmavbackend.modules.announcement.dto;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        Long courseId,
        String title,
        String body,
        String authorName,
        LocalDateTime createdAt
) {}

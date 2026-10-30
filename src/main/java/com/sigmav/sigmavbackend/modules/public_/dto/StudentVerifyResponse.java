package com.sigmav.sigmavbackend.modules.public_.dto;

import java.time.LocalDateTime;

public record StudentVerifyResponse(
        boolean valid,
        String studentName,
        String studentDni,
        String studentCode,
        String avatarUrl,
        String academyName,
        String enrolledIn,
        LocalDateTime enrolledAt,
        LocalDateTime expiresAt
) {}

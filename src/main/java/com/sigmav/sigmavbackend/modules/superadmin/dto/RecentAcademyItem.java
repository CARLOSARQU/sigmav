package com.sigmav.sigmavbackend.modules.superadmin.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RecentAcademyItem(
        Long id,
        UUID uuid,
        String name,
        String subdomain,
        String planType,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime subscriptionEnd
) {}

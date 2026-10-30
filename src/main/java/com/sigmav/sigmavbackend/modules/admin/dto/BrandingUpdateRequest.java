package com.sigmav.sigmavbackend.modules.admin.dto;

import java.util.List;

public record BrandingUpdateRequest(
        String logoUrl,
        String primaryColor,
        List<String> features
) {}

package com.sigmav.sigmavbackend.modules.public_.dto;

import java.util.List;

public record BrandingResponse(
        String academyName,
        String subdomain,
        String primaryColor,
        String logoUrl,
        List<String> features
) {}

package com.sigmav.sigmavbackend.modules.simulacro.dto;

import java.util.List;

public record UniversityResponse(
        Long id,
        String name,
        List<AreaResponse> areas
) {}

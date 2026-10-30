package com.sigmav.sigmavbackend.modules.simulacro.dto;

import java.util.List;

public record AreaResponse(
        Long id,
        String name,
        String universityName,
        List<CareerResponse> careers
) {}

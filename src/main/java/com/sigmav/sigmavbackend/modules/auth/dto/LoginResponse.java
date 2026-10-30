package com.sigmav.sigmavbackend.modules.auth.dto;

import java.util.List;
import java.util.Map;

public record LoginResponse(
        String token,
        String refreshToken,
        String email,
        String fullName,
        String role,
        Long academyId,
        Map<String, Boolean> features,
        List<String> permissions
) {}

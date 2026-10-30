package com.sigmav.sigmavbackend.modules.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SuperAdminBroadcastRequest(
        @NotBlank String title,
        @NotBlank String message,
        @NotNull @Pattern(regexp = "ALL|SPECIFIC") String targetAcademies,
        Long academyId,
        @NotNull @Pattern(regexp = "ADMINS|ALL") String recipients
) {}

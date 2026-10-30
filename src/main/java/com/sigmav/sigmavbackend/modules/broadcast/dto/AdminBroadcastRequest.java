package com.sigmav.sigmavbackend.modules.broadcast.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AdminBroadcastRequest(
        @NotBlank String title,
        @NotBlank String message,
        @NotNull @Pattern(regexp = "STUDENTS|TEACHERS|ALL|CYCLE") String recipients,
        Long cycleId
) {}

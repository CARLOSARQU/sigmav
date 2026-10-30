package com.sigmav.sigmavbackend.modules.permission.dto;

public record OverrideItem(
        String permissionName,
        boolean granted
) {}

package com.sigmav.sigmavbackend.modules.permission.dto;

import java.util.List;
import java.util.Set;

public record EffectivePermissionsResponse(
        Set<String> effectivePermissions,
        List<OverrideItem> overrides
) {}

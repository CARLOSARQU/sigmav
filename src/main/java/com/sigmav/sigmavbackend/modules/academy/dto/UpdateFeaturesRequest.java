package com.sigmav.sigmavbackend.modules.academy.dto;

import java.util.Map;

public record UpdateFeaturesRequest(
        Map<String, Boolean> features
) {}

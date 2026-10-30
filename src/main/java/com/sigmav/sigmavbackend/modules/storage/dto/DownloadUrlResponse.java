package com.sigmav.sigmavbackend.modules.storage.dto;

public record DownloadUrlResponse(
        String url,
        int expiresInSeconds
) {}

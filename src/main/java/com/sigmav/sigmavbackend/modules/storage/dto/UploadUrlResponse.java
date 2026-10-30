package com.sigmav.sigmavbackend.modules.storage.dto;

public record UploadUrlResponse(
        String uploadUrl,   // PUT a esta URL directamente desde el frontend
        String key          // Guardar este valor como fileUrl al crear el material
) {}

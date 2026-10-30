package com.sigmav.sigmavbackend.modules.course.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMaterialRequest(
        @NotBlank(message = "El título es requerido")
        String title,

        @NotBlank(message = "El tipo de material es requerido")
        String materialType,    // PDF_DRIVE | PDF_S3 | LINK | VIDEO

        String fileUrl,
        Long sectionId,         // opcional — si se quiere asociar a una sección
        Integer orderIndex
) {}

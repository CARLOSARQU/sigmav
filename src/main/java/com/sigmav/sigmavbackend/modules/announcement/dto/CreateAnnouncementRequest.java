package com.sigmav.sigmavbackend.modules.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementRequest(
        @NotBlank(message = "El título es requerido")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,

        @NotBlank(message = "El contenido es requerido")
        String body
) {}

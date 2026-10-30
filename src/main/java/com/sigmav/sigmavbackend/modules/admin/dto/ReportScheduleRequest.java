package com.sigmav.sigmavbackend.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReportScheduleRequest(
        @NotBlank(message = "El día es requerido")
        String reportScheduleDay,

        @Min(value = 0, message = "Hora inválida")
        @Max(value = 23, message = "Hora inválida")
        Integer reportScheduleHour
) {}

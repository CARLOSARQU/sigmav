package com.sigmav.sigmavbackend.modules.enrollment.dto;

import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** El estado de pago no se acepta aquí: es derivado en el servicio a partir de los montos
 * y la fecha de vencimiento. Los clientes que todavía lo envíen no fallan — Jackson lo ignora. */
public record CreateEnrollmentRequest(
        @NotNull(message = "El ID del estudiante es requerido")
        Long studentId,

        /** AREA | COURSE */
        @NotBlank(message = "El tipo de matrícula es requerido")
        String type,

        Long areaId,     // requerido si type = AREA
        Long courseId,   // requerido si type = COURSE

        BigDecimal amountPaid,
        BigDecimal totalAmount,
        String currency,
        PaymentMethod paymentMethod,
        LocalDateTime expiresAt,
        String notes
) {}

package com.sigmav.sigmavbackend.modules.enrollment.dto;

import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** El estado de pago no se acepta aquí: es derivado en el servicio a partir de los montos
 * y la fecha de vencimiento. Los clientes que todavía lo envíen no fallan — Jackson lo ignora. */
public record UpdateEnrollmentRequest(
        BigDecimal amountPaid,
        BigDecimal totalAmount,
        String currency,
        PaymentMethod paymentMethod,
        String notes,
        LocalDateTime expiresAt   // null = sin vencimiento
) {}

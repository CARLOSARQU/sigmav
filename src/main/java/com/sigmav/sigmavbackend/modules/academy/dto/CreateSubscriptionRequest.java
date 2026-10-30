package com.sigmav.sigmavbackend.modules.academy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateSubscriptionRequest(
        @NotBlank(message = "El tipo de plan es requerido")
        String planType,

        @NotNull(message = "El monto es requerido")
        @Positive(message = "El monto debe ser positivo")
        BigDecimal amountPaid,

        String currency,

        @NotNull(message = "La fecha de inicio es requerida")
        LocalDate periodStart,

        @NotNull(message = "La fecha de fin es requerida")
        LocalDate periodEnd,

        String paymentMethod,

        String notes
) {}

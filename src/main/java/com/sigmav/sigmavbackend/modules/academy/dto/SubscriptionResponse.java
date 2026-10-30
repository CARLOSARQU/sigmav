package com.sigmav.sigmavbackend.modules.academy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id,
        Long academyId,
        String planType,
        BigDecimal amountPaid,
        String currency,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String paymentMethod,
        String notes,
        LocalDateTime createdAt
) {}

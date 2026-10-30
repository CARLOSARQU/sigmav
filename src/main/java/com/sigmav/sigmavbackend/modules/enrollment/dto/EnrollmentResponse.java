package com.sigmav.sigmavbackend.modules.enrollment.dto;

import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentMethod;
import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long academyId,
        Long studentId,
        String enrollmentType,
        Long areaId,
        String areaName,
        Long courseId,
        String courseName,
        BigDecimal amountPaid,
        BigDecimal totalAmount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String notes,
        LocalDateTime enrolledAt,
        LocalDateTime expiresAt,
        Boolean isActive
) {}

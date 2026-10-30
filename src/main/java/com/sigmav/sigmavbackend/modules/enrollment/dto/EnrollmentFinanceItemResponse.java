package com.sigmav.sigmavbackend.modules.enrollment.dto;

import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EnrollmentFinanceItemResponse(
        Long id,
        Long studentId,
        String studentName,
        String studentDni,
        String enrollmentType,
        /** Nombre del área resuelto por el backend; null si la matrícula es por curso o el área ya no existe. */
        String areaName,
        /** Nombre del curso resuelto por el backend; null si la matrícula es por área o el curso ya no existe. */
        String courseName,
        BigDecimal amountPaid,
        BigDecimal totalAmount,
        String currency,
        String paymentMethod,
        PaymentStatus paymentStatus,
        String notes,
        LocalDateTime enrolledAt,
        LocalDateTime expiresAt,
        Boolean isActive
) {
    public BigDecimal amountOwed() {
        if (totalAmount == null || amountPaid == null) return null;
        return totalAmount.subtract(amountPaid).max(BigDecimal.ZERO);
    }
}

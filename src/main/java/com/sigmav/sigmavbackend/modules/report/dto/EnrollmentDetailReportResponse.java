package com.sigmav.sigmavbackend.modules.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EnrollmentDetailReportResponse(
        long total,
        List<Item> items
) {
    public record Item(
            int numero,
            Long enrollmentId,
            String dni,
            String studentName,
            String programa,        // Nombre del área o curso
            String enrollmentType,  // AREA | COURSE
            LocalDateTime enrolledAt,
            LocalDateTime expiresAt,
            BigDecimal amountPaid,
            String currency,
            String paymentMethod,   // EFECTIVO | TRANSFERENCIA | DEPOSITO | YAPE_PLIN | OTRO
            String status           // ACTIVA | INACTIVA | VENCIDA
    ) {}
}

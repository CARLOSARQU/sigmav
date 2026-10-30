package com.sigmav.sigmavbackend.modules.enrollment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El estado de pago dejó de ser un campo del request: ahora es derivado en el servicio.
 * Este test fija la decisión de quitar el campo en lugar de dejarlo ignorado a mano:
 * un cliente que todavía mande paymentStatus no debe romperse, el campo se descarta.
 */
@JsonTest
@DisplayName("Requests de matrícula — compatibilidad con clientes que envían paymentStatus")
class EnrollmentRequestJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("create: un paymentStatus enviado por un cliente viejo se ignora, no falla")
    void create_ignoraPaymentStatusDelCliente() {
        String json = """
                {"studentId":1,"type":"AREA","areaId":5,"amountPaid":50,"totalAmount":120,
                 "paymentStatus":"VENCIDO","expiresAt":"2026-09-30T23:59:59"}
                """;

        CreateEnrollmentRequest request = jsonMapper.readValue(json, CreateEnrollmentRequest.class);

        assertThat(request.studentId()).isEqualTo(1L);
        assertThat(request.areaId()).isEqualTo(5L);
        assertThat(request.amountPaid()).isEqualByComparingTo("50");
        assertThat(request.totalAmount()).isEqualByComparingTo("120");
        assertThat(request.expiresAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 59, 59));
    }

    @Test
    @DisplayName("update: un paymentStatus enviado por un cliente viejo se ignora, no falla")
    void update_ignoraPaymentStatusDelCliente() {
        String json = """
                {"amountPaid":120,"totalAmount":120,"paymentStatus":"PAGADO"}
                """;

        UpdateEnrollmentRequest request = jsonMapper.readValue(json, UpdateEnrollmentRequest.class);

        assertThat(request.amountPaid()).isEqualByComparingTo("120");
        assertThat(request.totalAmount()).isEqualByComparingTo("120");
    }
}

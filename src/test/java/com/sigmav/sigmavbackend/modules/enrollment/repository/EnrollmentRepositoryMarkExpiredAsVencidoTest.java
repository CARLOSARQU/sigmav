package com.sigmav.sigmavbackend.modules.enrollment.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code markExpiredAsVencido} es una consulta nativa: solo una base de datos real
 * puede demostrar qué filas toca. El test corre contra el mismo Postgres que usa el
 * contexto de Spring y la transacción hace rollback al terminar, así que no deja
 * rastro. Las filas de prueba se crean aquí dentro (academia → ciclo → área →
 * matrícula) para no depender de datos sembrados.
 *
 * <p>Regla bajo prueba: VENCIDO solo aplica a una matrícula vencida con un total
 * acordado (&gt; 0) que no está pagada por completo. Una matrícula sin total
 * acordado NUNCA pasa a VENCIDO, porque Finanzas cuenta VENCIDO como deuda y esas
 * filas inflaban el KPI.
 */
@SpringBootTest
@Transactional
@DisplayName("EnrollmentRepository.markExpiredAsVencido — VENCIDO exige total acordado")
class EnrollmentRepositoryMarkExpiredAsVencidoTest {

    private static final LocalDateTime AYER   = LocalDateTime.now().minusDays(1);
    private static final LocalDateTime MANANA = LocalDateTime.now().plusDays(1);
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO = BigDecimal.ZERO;

    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long academyId;
    private Long studentId;
    private Long areaId;

    @BeforeEach
    void crearFixtures() {
        academyId = jdbcTemplate.queryForObject(
                "INSERT INTO academies (name, subdomain) VALUES (?, ?) RETURNING id",
                Long.class, "Academia Vencido", "vencido-" + UUID.randomUUID());

        studentId = jdbcTemplate.queryForObject(
                "INSERT INTO students (academy_id, first_name, last_name, dni) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, academyId, "Ana", "Vencida", "T" + UUID.randomUUID().toString().substring(0, 8));

        Long cycleId = jdbcTemplate.queryForObject(
                "INSERT INTO cycles (academy_id, name, start_date, end_date) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, academyId, "Ciclo Test", LocalDate.now(), LocalDate.now().plusMonths(1));

        areaId = jdbcTemplate.queryForObject(
                "INSERT INTO areas (academy_id, cycle_id, name) VALUES (?, ?, ?) RETURNING id",
                Long.class, academyId, cycleId, "Área Test");
    }

    /**
     * Inserta una matrícula de área con los montos, el vencimiento y el estado indicados.
     * Devuelve su id para poder leerla después del UPDATE sin depender del conteo total
     * (la base de dev tiene filas propias que el job también puede tocar).
     */
    private Long insertarMatricula(BigDecimal totalAmount, BigDecimal amountPaid,
                                   LocalDateTime expiresAt, boolean isActive, String paymentStatus) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO enrollments
                    (academy_id, student_id, enrollment_type, area_id, total_amount, amount_paid,
                     expires_at, is_active, payment_status)
                VALUES (?, ?, 'AREA', ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                academyId, studentId, areaId, totalAmount, amountPaid, expiresAt, isActive, paymentStatus);
    }

    private String estadoDe(Long enrollmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT payment_status FROM enrollments WHERE id = ?", String.class, enrollmentId);
    }

    @Test
    @DisplayName("markExpiredAsVencido: vencida sin total acordado → nunca VENCIDO (se queda PENDIENTE)")
    void vencidaSinTotalAcordado_noPasaAVencido() {
        Long sinTotal = insertarMatricula(null, null, AYER, true, "PENDIENTE");
        Long totalCero = insertarMatricula(CERO, CERO, AYER, true, "PENDIENTE");
        Long totalCeroConPago = insertarMatricula(CERO, new BigDecimal("50"), AYER, true, "PENDIENTE");
        // Eje total <= 0 sin pago registrado: la guarda es total_amount > 0, no "no está pagada".
        // Sin estos casos, un predicado que sólo exija amount_paid IS NULL pasaría inadvertido.
        Long totalCeroSinPago = insertarMatricula(CERO, null, AYER, true, "PENDIENTE");
        Long totalCeroConPagoNegativo = insertarMatricula(CERO, new BigDecimal("-50"), AYER, true, "PENDIENTE");
        Long totalNegativoSinPago = insertarMatricula(new BigDecimal("-100"), null, AYER, true, "PENDIENTE");

        enrollmentRepository.markExpiredAsVencido();

        // Finanzas cuenta VENCIDO como deuda: sin total acordado no hay deuda que cobrar
        assertThat(estadoDe(sinTotal)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(totalCero)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(totalCeroConPago)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(totalCeroSinPago)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(totalCeroConPagoNegativo)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(totalNegativoSinPago)).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("markExpiredAsVencido: vencida con total acordado e impaga → VENCIDO")
    void vencidaConTotalAcordadoEImpaga_pasaAVencido() {
        Long sinPago = insertarMatricula(CIEN, null, AYER, true, "PENDIENTE");
        Long pagoCero = insertarMatricula(CIEN, CERO, AYER, true, "PENDIENTE");
        Long pagoParcial = insertarMatricula(CIEN, new BigDecimal("50"), AYER, true, "PARCIAL");

        enrollmentRepository.markExpiredAsVencido();

        assertThat(estadoDe(sinPago)).isEqualTo("VENCIDO");
        assertThat(estadoDe(pagoCero)).isEqualTo("VENCIDO");
        assertThat(estadoDe(pagoParcial)).isEqualTo("VENCIDO");
    }

    @Test
    @DisplayName("markExpiredAsVencido: vencida y pagada por completo → se queda PAGADO")
    void vencidaPagadaPorCompleto_noPasaAVencido() {
        Long pagada = insertarMatricula(CIEN, CIEN, AYER, true, "PAGADO");
        Long pagadaDeMas = insertarMatricula(CIEN, new BigDecimal("150"), AYER, true, "PAGADO");

        enrollmentRepository.markExpiredAsVencido();

        assertThat(estadoDe(pagada)).isEqualTo("PAGADO");
        assertThat(estadoDe(pagadaDeMas)).isEqualTo("PAGADO");
    }

    @Test
    @DisplayName("markExpiredAsVencido: fuera de alcance (inactiva, sin vencer, ya VENCIDO) → intactas")
    void fueraDeAlcance_noSeTocan() {
        Long inactiva        = insertarMatricula(CIEN, null, AYER, false, "PENDIENTE");
        Long sinVencer       = insertarMatricula(CIEN, null, MANANA, true, "PENDIENTE");
        Long sinVencimiento  = insertarMatricula(CIEN, null, null, true, "PENDIENTE");
        Long yaVencida       = insertarMatricula(CIEN, null, AYER, true, "VENCIDO");

        enrollmentRepository.markExpiredAsVencido();

        assertThat(estadoDe(inactiva)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(sinVencer)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(sinVencimiento)).isEqualTo("PENDIENTE");
        assertThat(estadoDe(yaVencida)).isEqualTo("VENCIDO");
    }
}

package com.sigmav.sigmavbackend.modules.enrollment.service;

import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.area.service.AreaService;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.enrollment.dto.CreateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentFinanceItemResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.EnrollmentResponse;
import com.sigmav.sigmavbackend.modules.enrollment.dto.UpdateEnrollmentRequest;
import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentStatus;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.StudentService;
import com.sigmav.sigmavbackend.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService — Matrícula y control de acceso")
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentService studentService;
    @Mock private AreaService areaService;
    @Mock private CourseService courseService;
    @Mock private CourseRepository courseRepository;
    @Mock private AreaRepository areaRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    // ── SETUP Y TEARDOWN ─────────────────────────────────────────────────────
    // TenantContext usa un ThreadLocal estático. No es un bean de Spring,
    // así que Mockito no puede mockearlo automáticamente.
    //
    // Solución: lo seteamos manualmente en @BeforeEach (como haría el TenantInterceptor
    // en producción) y lo limpiamos en @AfterEach para no contaminar otros tests.
    // Sin limpiar, el ThreadLocal mantendría el valor entre tests del mismo hilo.

    @BeforeEach
    void configurarTenant() {
        TenantContext.setCurrentAcademyId(1L);
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    // =========================================================================
    // hasAccessToCourse — lógica de acceso B5.5
    // =========================================================================

    @Test
    @DisplayName("hasAccessToCourse: matrícula directa por curso activa → true")
    void hasAccessToCourse_matriculaDirectaActiva_retornaTrue() {
        Long studentId = 1L;
        Long courseId = 10L;

        // El repo confirma que existe matrícula activa directa por curso
        when(enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId))
                .thenReturn(true);

        boolean result = enrollmentService.hasAccessToCourse(studentId, courseId);

        assertThat(result).isTrue();
        // Cuando hay matrícula directa, NO se necesita consultar por área → eficiencia
    }

    @Test
    @DisplayName("hasAccessToCourse: matrícula por área activa → true (acceso indirecto)")
    void hasAccessToCourse_matriculaAreaActiva_retornaTrue() {
        Long studentId = 1L;
        Long courseId = 10L;
        Long areaId = 5L;

        // No tiene matrícula directa por curso...
        when(enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId))
                .thenReturn(false);

        // ...pero el curso pertenece al área 5...
        Course curso = Course.builder().id(courseId).areaId(areaId).build();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(curso));

        // ...y sí tiene matrícula activa por esa área → acceso concedido
        when(enrollmentRepository.existsActiveAreaEnrollment(studentId, areaId))
                .thenReturn(true);

        boolean result = enrollmentService.hasAccessToCourse(studentId, courseId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasAccessToCourse: sin ninguna matrícula activa → false")
    void hasAccessToCourse_sinMatricula_retornaFalse() {
        Long studentId = 1L;
        Long courseId = 10L;
        Long areaId = 5L;

        when(enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId))
                .thenReturn(false);
        Course curso = Course.builder().id(courseId).areaId(areaId).build();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(curso));
        when(enrollmentRepository.existsActiveAreaEnrollment(studentId, areaId))
                .thenReturn(false);

        boolean result = enrollmentService.hasAccessToCourse(studentId, courseId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasAccessToCourse: curso no existe → false (no lanza excepción)")
    void hasAccessToCourse_cursoNoExiste_retornaFalse() {
        Long studentId = 1L;
        Long courseId = 99L;

        when(enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId))
                .thenReturn(false);
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        // El servicio usa .orElse(false), así que no lanza excepción — devuelve false
        boolean result = enrollmentService.hasAccessToCourse(studentId, courseId);

        assertThat(result).isFalse();
    }

    // =========================================================================
    // assertStudentAccess
    // =========================================================================

    @Test
    @DisplayName("assertStudentAccess: sin acceso → AccessDeniedException")
    void assertStudentAccess_sinAcceso_lanzaAccessDenied() {
        Long studentId = 1L;
        Long courseId = 10L;

        when(enrollmentRepository.existsActiveCourseEnrollment(studentId, courseId))
                .thenReturn(false);
        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        // assertStudentAccess llama a hasAccessToCourse internamente
        // Si retorna false → lanza AccessDeniedException
        assertThatThrownBy(() -> enrollmentService.assertStudentAccess(studentId, courseId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("matrícula");
    }

    // =========================================================================
    // create — validaciones de entrada
    // =========================================================================

    @Test
    @DisplayName("create: tipo inválido → IllegalArgumentException con mensaje claro")
    void create_tipoInvalido_lanzaIllegalArgument() {
        // findOrFail() retorna Student pero EnrollmentService.create no usa el valor devuelto.
        // Mockito retorna null por defecto para métodos no-void no stubbeados → es suficiente.
        // No necesitamos stub aquí: el código solo llama findOrFail() para validar existencia
        // (lanza NotFoundException si no existe) y en el mock simplemente retorna null sin lanzar.

        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                1L,       // studentId
                "BECA",   // tipo inválido — no es AREA ni COURSE
                null,     // areaId
                null,     // courseId
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> enrollmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BECA")      // el mensaje incluye el tipo inválido
                .hasMessageContaining("AREA")      // y muestra los tipos válidos
                .hasMessageContaining("COURSE");
    }

    @Test
    @DisplayName("create: tipo AREA sin areaId → IllegalArgumentException")
    void create_tipoArea_sinAreaId_lanzaIllegalArgument() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                1L,
                "AREA",
                null,   // ← areaId faltante
                null,
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> enrollmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("areaId");
    }

    @Test
    @DisplayName("create: tipo COURSE sin courseId → IllegalArgumentException")
    void create_tipoCourse_sinCourseId_lanzaIllegalArgument() {
        CreateEnrollmentRequest request = new CreateEnrollmentRequest(
                1L,
                "COURSE",
                null,
                null,   // ← courseId faltante
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> enrollmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("courseId");
    }

    // =========================================================================
    // derivePaymentStatus — el estado de pago es derivado, nunca manual
    // VENCIDO solo aplica a matrículas impagas con total acordado. Una matrícula
    // pagada por completo nunca cae en VENCIDO: Finanzas cuenta VENCIDO como deuda,
    // así que marcarla inflaba el KPI.
    // =========================================================================

    private static final BigDecimal CIENTO_VEINTE = new BigDecimal("120");
    private static final LocalDateTime AYER   = LocalDateTime.now().minusDays(1);
    private static final LocalDateTime MANANA = LocalDateTime.now().plusDays(1);

    @Test
    @DisplayName("derivePaymentStatus: impaga y vencida → VENCIDO")
    void derivePaymentStatus_impagaYVencida_retornaVencido() {
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("50"), CIENTO_VEINTE, AYER))
                .isEqualTo(PaymentStatus.VENCIDO);
        // Nada pagado (null o cero) también es impaga
        assertThat(EnrollmentService.derivePaymentStatus(null, CIENTO_VEINTE, AYER))
                .isEqualTo(PaymentStatus.VENCIDO);
        assertThat(EnrollmentService.derivePaymentStatus(BigDecimal.ZERO, CIENTO_VEINTE, AYER))
                .isEqualTo(PaymentStatus.VENCIDO);
    }

    @Test
    @DisplayName("derivePaymentStatus: pagada por completo y vencida → nunca VENCIDO")
    void derivePaymentStatus_pagadaPorCompletoYVencida_retornaPagado() {
        assertThat(EnrollmentService.derivePaymentStatus(CIENTO_VEINTE, CIENTO_VEINTE, AYER))
                .isEqualTo(PaymentStatus.PAGADO);
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("150"), CIENTO_VEINTE, AYER))
                .isEqualTo(PaymentStatus.PAGADO);
    }

    @Test
    @DisplayName("derivePaymentStatus: sin total acordado → PENDIENTE, vencida o no")
    void derivePaymentStatus_sinTotalAcordado_retornaPendiente() {
        assertThat(EnrollmentService.derivePaymentStatus(null, null, AYER))
                .isEqualTo(PaymentStatus.PENDIENTE);
        assertThat(EnrollmentService.derivePaymentStatus(null, null, null))
                .isEqualTo(PaymentStatus.PENDIENTE);
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("50"), null, AYER))
                .isEqualTo(PaymentStatus.PENDIENTE);
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("50"), BigDecimal.ZERO, AYER))
                .isEqualTo(PaymentStatus.PENDIENTE);
    }

    @Test
    @DisplayName("derivePaymentStatus: sin nada pagado y vigente → PENDIENTE")
    void derivePaymentStatus_sinNadaPagado_retornaPendiente() {
        assertThat(EnrollmentService.derivePaymentStatus(null, CIENTO_VEINTE, null))
                .isEqualTo(PaymentStatus.PENDIENTE);
        assertThat(EnrollmentService.derivePaymentStatus(BigDecimal.ZERO, CIENTO_VEINTE, MANANA))
                .isEqualTo(PaymentStatus.PENDIENTE);
    }

    @Test
    @DisplayName("derivePaymentStatus: pago parcial → PARCIAL; pago que cubre el total → PAGADO")
    void derivePaymentStatus_montosParciales_retornanParcialOPagado() {
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("50"), CIENTO_VEINTE, null))
                .isEqualTo(PaymentStatus.PARCIAL);
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("50"), CIENTO_VEINTE, MANANA))
                .isEqualTo(PaymentStatus.PARCIAL);
        assertThat(EnrollmentService.derivePaymentStatus(CIENTO_VEINTE, CIENTO_VEINTE, null))
                .isEqualTo(PaymentStatus.PAGADO);
        assertThat(EnrollmentService.derivePaymentStatus(new BigDecimal("150"), CIENTO_VEINTE, MANANA))
                .isEqualTo(PaymentStatus.PAGADO);
    }

    // =========================================================================
    // create / update — el estado de pago se calcula en el servidor
    // =========================================================================

    private CreateEnrollmentRequest peticionDeArea(BigDecimal pagado, BigDecimal total) {
        return new CreateEnrollmentRequest(1L, "AREA", 5L, null, pagado, total, null, null, null, null);
    }

    @Test
    @DisplayName("create: deriva el estado de pago a partir de los montos")
    void create_derivaElEstadoDePago() {
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse result = enrollmentService.create(
                peticionDeArea(new BigDecimal("50"), CIENTO_VEINTE));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PARCIAL);
    }

    @Test
    @DisplayName("create: sin montos registrados → PENDIENTE")
    void create_sinMontos_retornaPendiente() {
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse result = enrollmentService.create(peticionDeArea(null, null));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PENDIENTE);
    }

    @Test
    @DisplayName("update: recalcula el estado de pago con los montos ya aplicados")
    void update_recalculaElEstadoDePago() {
        Enrollment enrollment = Enrollment.builder()
                .id(1L).academyId(1L).studentId(1L).enrollmentType("AREA").areaId(5L)
                .amountPaid(new BigDecimal("50")).totalAmount(CIENTO_VEINTE)
                .isActive(true).enrolledAt(LocalDateTime.now())
                .build();
        when(enrollmentRepository.findByIdAndAcademyId(1L, 1L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse result = enrollmentService.update(1L,
                new UpdateEnrollmentRequest(CIENTO_VEINTE, null, null, null, null, null));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAGADO);
    }

    @Test
    @DisplayName("update: un VENCIDO marcado a mano no sobrevive si la matrícula está pagada")
    void update_ignoraElVencidoMarcadoAMano() {
        Enrollment enrollment = Enrollment.builder()
                .id(1L).academyId(1L).studentId(1L).enrollmentType("AREA").areaId(5L)
                .amountPaid(CIENTO_VEINTE).totalAmount(CIENTO_VEINTE)
                .paymentStatus(PaymentStatus.VENCIDO)
                .isActive(true).enrolledAt(LocalDateTime.now())
                .build();
        when(enrollmentRepository.findByIdAndAcademyId(1L, 1L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Aunque la fecha de acceso ya pasó, una matrícula pagada por completo no es deuda
        EnrollmentResponse result = enrollmentService.update(1L,
                new UpdateEnrollmentRequest(null, null, null, null, null, AYER));

        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PAGADO);
    }

    // =========================================================================
    // listByStudent — nombres de área/curso
    // El admin necesita ver el programa (nombre), no solo su id. Los nombres se
    // resuelven en el backend y NUNCA deben romper el listado: si el área o el
    // curso ya no existe, el nombre queda en null en lugar de lanzar excepción.
    // =========================================================================

    private Enrollment matriculaPorArea(Long areaId) {
        return Enrollment.builder()
                .id(1L)
                .academyId(1L)
                .studentId(1L)
                .enrollmentType("AREA")
                .areaId(areaId)
                .isActive(true)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    private Enrollment matriculaPorCurso(Long courseId) {
        return Enrollment.builder()
                .id(2L)
                .academyId(1L)
                .studentId(1L)
                .enrollmentType("COURSE")
                .courseId(courseId)
                .isActive(true)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("listByStudent: matrícula AREA → resuelve areaName, courseName queda en null")
    void listByStudent_matriculaArea_resuelveNombreDelArea() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaPorArea(5L)));

        Area area = Area.builder().id(5L).academyId(1L).name("Matemática").isActive(true).build();
        when(areaRepository.findByIdInAndAcademyId(Set.of(5L), 1L)).thenReturn(List.of(area));

        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).areaName()).isEqualTo("Matemática");
        // El campo que no corresponde al tipo de matrícula no se resuelve
        assertThat(result.get(0).courseName()).isNull();
    }

    @Test
    @DisplayName("listByStudent: matrícula COURSE → resuelve courseName, areaName queda en null")
    void listByStudent_matriculaCurso_resuelveNombreDelCurso() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaPorCurso(10L)));

        Course curso = Course.builder().id(10L).academyId(1L).areaId(5L).name("Álgebra").isActive(true).build();
        when(courseRepository.findByIdInAndAcademyId(Set.of(10L), 1L)).thenReturn(List.of(curso));

        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseName()).isEqualTo("Álgebra");
        assertThat(result.get(0).areaName()).isNull();
    }

    @Test
    @DisplayName("listByStudent: área eliminada → areaName null, sin lanzar excepción")
    void listByStudent_areaEliminada_areaNameNull() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaPorArea(99L)));
        when(areaRepository.findByIdInAndAcademyId(Set.of(99L), 1L)).thenReturn(List.of());

        // El listado no se rompe: el nombre simplemente no está disponible
        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).areaName()).isNull();
    }

    @Test
    @DisplayName("listByStudent: área desactivada → igual resuelve el nombre (matrícula histórica)")
    void listByStudent_areaDesactivada_resuelveNombre() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaPorArea(5L)));

        // El nombre se busca sin filtrar por isActive: el histórico debe verse igual
        Area area = Area.builder().id(5L).academyId(1L).name("Matemática").isActive(false).build();
        when(areaRepository.findByIdInAndAcademyId(Set.of(5L), 1L)).thenReturn(List.of(area));

        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result.get(0).areaName()).isEqualTo("Matemática");
    }

    @Test
    @DisplayName("listByStudent: curso eliminado → courseName null, sin lanzar excepción")
    void listByStudent_cursoEliminado_courseNameNull() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaPorCurso(99L)));
        when(courseRepository.findByIdInAndAcademyId(Set.of(99L), 1L)).thenReturn(List.of());

        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseName()).isNull();
    }

    @Test
    @DisplayName("listByStudent: resuelve los nombres en batch, sin una consulta por fila")
    void listByStudent_resuelveNombresEnBatch() {
        when(enrollmentRepository.findByStudentIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(
                        matriculaPorArea(5L),
                        matriculaPorArea(6L),
                        matriculaPorCurso(10L),
                        matriculaPorCurso(11L)));

        when(areaRepository.findByIdInAndAcademyId(Set.of(5L, 6L), 1L))
                .thenReturn(List.of(
                        Area.builder().id(5L).academyId(1L).name("Biomédicas").build(),
                        Area.builder().id(6L).academyId(1L).name("Ingenierías").build()));
        when(courseRepository.findByIdInAndAcademyId(Set.of(10L, 11L), 1L))
                .thenReturn(List.of(
                        Course.builder().id(10L).academyId(1L).name("Aritmética").build(),
                        Course.builder().id(11L).academyId(1L).name("Álgebra").build()));

        List<EnrollmentResponse> result = enrollmentService.listByStudent(1L);

        assertThat(result).extracting(EnrollmentResponse::areaName)
                .containsExactly("Biomédicas", "Ingenierías", null, null);
        assertThat(result).extracting(EnrollmentResponse::courseName)
                .containsExactly(null, null, "Aritmética", "Álgebra");

        // Una sola consulta por relación para toda la lista
        verify(areaRepository, times(1)).findByIdInAndAcademyId(Set.of(5L, 6L), 1L);
        verify(courseRepository, times(1)).findByIdInAndAcademyId(Set.of(10L, 11L), 1L);
        verify(areaRepository, never()).findByIdAndAcademyId(any(), any());
        verify(courseRepository, never()).findByIdAndAcademyId(any(), any());
    }

    // =========================================================================
    // listForFinances — nombres del programa (Finanzas usa otro DTO)
    // Igual que listByStudent: nombres en batch y sin romper el listado si el
    // área o el curso ya no existe.
    // =========================================================================

    private Enrollment matriculaConId(Long id, Long areaId) {
        return Enrollment.builder()
                .id(id)
                .academyId(1L)
                .studentId(1L)
                .enrollmentType("AREA")
                .areaId(areaId)
                .isActive(true)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("listForFinances: resuelve areaName/courseName en batch y deja null el que falta")
    void listForFinances_resuelveNombresEnBatch() {
        when(enrollmentRepository.findByAcademyIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(matriculaConId(1L, 5L), matriculaConId(2L, 99L)));

        when(areaRepository.findByIdInAndAcademyId(Set.of(5L, 99L), 1L))
                .thenReturn(List.of(
                        Area.builder().id(5L).academyId(1L).name("Biomédicas").isActive(false).build()));

        List<EnrollmentFinanceItemResponse> result = enrollmentService.listForFinances(null);

        assertThat(result).hasSize(2);
        // El área desactivada igual muestra su nombre; la que ya no existe queda en null
        assertThat(result.get(0).areaName()).isEqualTo("Biomédicas");
        assertThat(result.get(1).areaName()).isNull();
        assertThat(result).allSatisfy(item -> assertThat(item.courseName()).isNull());

        // Dos filas → una sola consulta de áreas
        verify(areaRepository, times(1)).findByIdInAndAcademyId(Set.of(5L, 99L), 1L);
    }

    @Test
    @DisplayName("listForFinances: sin áreas ni cursos en la lista no consulta los repositorios")
    void listForFinances_sinRelaciones_noConsultaRepositorios() {
        when(enrollmentRepository.findByAcademyIdOrderByEnrolledAtDesc(1L))
                .thenReturn(List.of(Enrollment.builder()
                        .id(1L).academyId(1L).studentId(1L).enrollmentType("AREA")
                        .isActive(true).enrolledAt(LocalDateTime.now()).build()));

        List<EnrollmentFinanceItemResponse> result = enrollmentService.listForFinances(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).areaName()).isNull();
        verify(areaRepository, never()).findByIdInAndAcademyId(any(), any());
        verify(courseRepository, never()).findByIdInAndAcademyId(any(), any());
    }
}

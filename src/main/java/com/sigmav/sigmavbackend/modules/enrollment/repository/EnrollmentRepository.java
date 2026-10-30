package com.sigmav.sigmavbackend.modules.enrollment.repository;

import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByStudentIdOrderByEnrolledAtDesc(Long studentId);

    List<Enrollment> findByAcademyIdOrderByEnrolledAtDesc(Long academyId);

    List<Enrollment> findByAcademyIdAndPaymentStatusOrderByEnrolledAtDesc(Long academyId, PaymentStatus paymentStatus);

    Optional<Enrollment> findByIdAndAcademyId(Long id, Long academyId);

    /** Matrícula activa y no vencida por curso directo */
    @Query("""
            SELECT COUNT(e) > 0 FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.courseId  = :courseId
              AND e.enrollmentType = 'COURSE'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveCourseEnrollment(@Param("studentId") Long studentId,
                                         @Param("courseId") Long courseId);

    /** Matrícula activa y no vencida por área */
    @Query("""
            SELECT COUNT(e) > 0 FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.areaId    = :areaId
              AND e.enrollmentType = 'AREA'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveAreaEnrollment(@Param("studentId") Long studentId,
                                       @Param("areaId") Long areaId);

    /** Matrículas de área activas y no vencidas (para listar cursos del estudiante) */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.enrollmentType = 'AREA'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<Enrollment> findActiveAreaEnrollments(@Param("studentId") Long studentId);

    /** Matrículas de curso activas y no vencidas (para listar cursos del estudiante) */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.enrollmentType = 'COURSE'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<Enrollment> findActiveCourseEnrollments(@Param("studentId") Long studentId);

    /** IDs de estudiantes con matrícula activa en un curso (directa o por área).
     * Usado para construir la lista de asistencia (B6.7). */
    @Query("""
            SELECT DISTINCT e.studentId FROM Enrollment e
            WHERE e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
              AND ((e.enrollmentType = 'COURSE' AND e.courseId = :courseId)
                OR (e.enrollmentType = 'AREA'   AND e.areaId   = :areaId))
            """)
    List<Long> findEnrolledStudentIds(@Param("courseId") Long courseId,
                                      @Param("areaId") Long areaId);

    /** IDs de todos los estudiantes con matrícula activa en la academia.
     * Usado para notificaciones de alcance CYCLE. */
    @Query("""
            SELECT DISTINCT e.studentId FROM Enrollment e
            WHERE e.academyId = :academyId
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<Long> findAllActiveStudentIds(@Param("academyId") Long academyId);

    // ── Reportes (B8) ─────────────────────────────────────────────────────────

    /** B8.3 — conteo por curso (tipo COURSE) */
    long countByIsActiveTrueAndCourseIdAndEnrollmentType(Long courseId, String enrollmentType);

    /** B8.3 — conteo por área (tipo AREA) */
    long countByIsActiveTrueAndAreaIdAndEnrollmentType(Long areaId, String enrollmentType);

    /** B8.4 — matrículas con monto registrado en el rango de fechas, filtradas por academia */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.amountPaid IS NOT NULL
              AND e.academyId = :academyId
              AND (CAST(:from AS timestamp) IS NULL OR e.enrolledAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.enrolledAt <= :to)
            ORDER BY e.enrolledAt
            """)
    List<Enrollment> findForRevenue(@Param("academyId") Long academyId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /** B8.3 — total activas en la academia (con academyId explícito) */
    long countByAcademyIdAndIsActiveTrue(Long academyId);

    /** B8.x — reporte detallado: todas las matrículas de la academia con filtros opcionales */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.academyId = :academyId
              AND (:courseId IS NULL OR (e.enrollmentType = 'COURSE' AND e.courseId = :courseId))
              AND (:areaId   IS NULL OR (e.enrollmentType = 'AREA'   AND e.areaId   = :areaId))
              AND (CAST(:from AS timestamp) IS NULL OR e.enrolledAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR e.enrolledAt <= :to)
            ORDER BY e.enrolledAt DESC
            """)
    List<Enrollment> findForDetail(@Param("academyId") Long academyId,
                                   @Param("courseId") Long courseId,
                                   @Param("areaId") Long areaId,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    /** B8.x — reporte detallado filtrado por ciclo (busca por listas de areaIds y courseIds) */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE e.academyId = :academyId
              AND ((e.enrollmentType = 'AREA'   AND e.areaId   IN :areaIds)
                OR (e.enrollmentType = 'COURSE' AND e.courseId IN :courseIds))
              AND (CAST(:from AS timestamp) IS NULL OR e.enrolledAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR e.enrolledAt <= :to)
            ORDER BY e.enrolledAt DESC
            """)
    List<Enrollment> findForDetailByCycleScope(@Param("academyId") Long academyId,
                                               @Param("areaIds") List<Long> areaIds,
                                               @Param("courseIds") List<Long> courseIds,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** Indica si el estudiante tiene al menos una matrícula activa y no vencida */
    @Query("""
            SELECT COUNT(e) > 0 FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    boolean existsActiveEnrollmentForStudent(@Param("studentId") Long studentId);

    /** IDs del subconjunto de studentIds que tienen al menos una matrícula activa — batch load */
    @Query("""
            SELECT DISTINCT e.studentId FROM Enrollment e
            WHERE e.studentId IN :studentIds
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    Set<Long> findActiveEnrollmentStudentIds(@Param("studentIds") Collection<Long> studentIds);

    /** IDs de estudiantes matriculados en alguna de las áreas o cursos dados — para difusión por ciclo */
    @Query("""
            SELECT DISTINCT e.studentId FROM Enrollment e
            WHERE e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
              AND ((e.enrollmentType = 'AREA'   AND e.areaId   IN :areaIds)
                OR (e.enrollmentType = 'COURSE' AND e.courseId IN :courseIds))
            """)
    List<Long> findStudentIdsByAreasOrCourses(@Param("areaIds") Collection<Long> areaIds,
                                              @Param("courseIds") Collection<Long> courseIds);

    /** IDs de cursos con matrícula activa directa — batch load para filtrado de exámenes */
    @Query("""
            SELECT e.courseId FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.enrollmentType = 'COURSE'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    Set<Long> findEnrolledCourseIds(@Param("studentId") Long studentId);

    /** IDs de áreas con matrícula activa directa — batch load para filtrado de exámenes */
    @Query("""
            SELECT e.areaId FROM Enrollment e
            WHERE e.studentId = :studentId
              AND e.enrollmentType = 'AREA'
              AND e.isActive = true
              AND (e.expiresAt IS NULL OR e.expiresAt > CURRENT_TIMESTAMP)
            """)
    Set<Long> findEnrolledAreaIds(@Param("studentId") Long studentId);

    /** B8.7 — cantidad de matrículas creadas en un periodo */
    long countByAcademyIdAndEnrolledAtBetween(Long academyId, LocalDateTime from, LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(e.amountPaid), 0)
            FROM Enrollment e
            WHERE e.academyId = :academyId
              AND e.amountPaid IS NOT NULL
              AND e.enrolledAt BETWEEN :from AND :to
            """)
    BigDecimal sumAmountPaidInPeriodByAcademy(@Param("academyId") Long academyId,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    // Returns rows: [paymentStatus (String), count (Long)]
    @Query("SELECT e.paymentStatus, COUNT(e) FROM Enrollment e WHERE e.academyId = :academyId AND e.isActive = true GROUP BY e.paymentStatus")
    List<Object[]> countByPaymentStatusGrouped(@Param("academyId") Long academyId);

    // Returns rows: [month (String "YYYY-MM"), revenue (BigDecimal), count (Long)]
    @Query(value = "SELECT TO_CHAR(e.enrolled_at, 'YYYY-MM') AS month, COALESCE(SUM(e.amount_paid), 0) AS revenue, COUNT(e.id) AS cnt " +
                   "FROM enrollments e " +
                   "WHERE e.academy_id = :academyId AND e.enrolled_at >= :from AND e.amount_paid IS NOT NULL " +
                   "GROUP BY TO_CHAR(e.enrolled_at, 'YYYY-MM') " +
                   "ORDER BY month ASC",
           nativeQuery = true)
    List<Object[]> monthlyRevenueSeries(@Param("academyId") Long academyId, @Param("from") LocalDateTime from);

    /** Marca VENCIDO las matrículas vencidas que tienen un total acordado (> 0) y no
     * están pagadas por completo. Misma regla que {@code EnrollmentService.derivePaymentStatus}:
     * VENCIDO solo aplica a matrículas impagas con un total acordado, así que una matrícula
     * pagada por completo — o una sin total acordado — nunca cae en VENCIDO. */
    @Modifying
    @Query(value = """
        UPDATE enrollments
        SET payment_status = 'VENCIDO', updated_at = NOW()
        WHERE is_active = true
          AND expires_at IS NOT NULL
          AND expires_at < NOW()
          AND total_amount IS NOT NULL
          AND total_amount > 0
          AND (amount_paid IS NULL OR amount_paid < total_amount)
          AND (payment_status IS NULL OR payment_status <> 'VENCIDO')
        """, nativeQuery = true)
    int markExpiredAsVencido();
}
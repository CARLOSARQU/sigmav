package com.sigmav.sigmavbackend.modules.session.repository;

import com.sigmav.sigmavbackend.modules.session.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findBySessionId(Long sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    boolean existsBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** Para B8.2 — registros de varios sesiones (reporte por estudiante) */
    List<AttendanceRecord> findBySessionIdIn(List<Long> sessionIds);

    /** Para B8.7 — tasa de asistencia promedio */
    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.status IN ('PRESENT', 'LATE')")
    long countAttended();

    /** Total de registros de asistencia para un estudiante desde una fecha */
    long countByStudentIdAndCheckInAtAfter(Long studentId, LocalDateTime since);

    /** Asistencias (PRESENT o LATE) de un estudiante desde una fecha */
    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.studentId = :studentId AND r.checkInAt >= :since AND r.status IN ('PRESENT', 'LATE')")
    long countAttendedByStudentIdSince(@Param("studentId") Long studentId, @Param("since") LocalDateTime since);

    long countByAcademyId(Long academyId);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.academyId = :academyId AND r.status IN ('PRESENT', 'LATE')")
    long countAttendedByAcademy(@Param("academyId") Long academyId);

    /**
     * Engagement — agrupado por estudiante: [studentId, attendedCount, totalCount]
     * Devuelve Object[] por estudiante para construir tasas en memoria sin N+1.
     */
    @Query(value = """
            SELECT ar.student_id,
                   SUM(CASE WHEN ar.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END) AS attended,
                   COUNT(*) AS total
            FROM attendance_records ar
            WHERE ar.academy_id = :academyId
            GROUP BY ar.student_id
            """, nativeQuery = true)
    List<Object[]> findAttendanceStatsByAcademy(@Param("academyId") Long academyId);
}

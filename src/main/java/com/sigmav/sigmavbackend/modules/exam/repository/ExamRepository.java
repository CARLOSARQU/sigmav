package com.sigmav.sigmavbackend.modules.exam.repository;

import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    List<Exam> findByAcademyIdOrderByCreatedAtDesc(Long academyId);

    /** Todos los exámenes publicados actualmente en ventana de horario (para el portal del estudiante) */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.academyId = :academyId
              AND e.status = 'PUBLISHED'
              AND (e.availableFrom IS NULL OR e.availableFrom <= :now)
              AND (e.availableUntil IS NULL OR e.availableUntil >= :now)
            ORDER BY e.title
            """)
    List<Exam> findAvailableNow(@Param("academyId") Long academyId, @Param("now") LocalTime now);

    /** Publicados + no expirados — incluye programados (availableFrom > now) para mostrarlos en el portal */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.academyId = :academyId
              AND e.status = 'PUBLISHED'
              AND (e.availableUntil IS NULL OR e.availableUntil >= :now)
            ORDER BY e.availableFrom ASC NULLS FIRST, e.title
            """)
    List<Exam> findAllPublishedForStudent(@Param("academyId") Long academyId, @Param("now") LocalTime now);

    Optional<Exam> findByIdAndAcademyId(Long id, Long academyId);

    /** B8.7 — conteo por estado */
    long countByStatus(String status);
    long countByAcademyIdAndStatus(Long academyId, String status);

    @Cacheable(value = "exam-max-score", key = "#examId")
    @Query("SELECT COALESCE(SUM(q.points), 0) FROM Question q WHERE q.examId = :examId")
    BigDecimal sumQuestionPoints(@Param("examId") Long examId);

    @Query("SELECT q.examId, COALESCE(SUM(q.points), 0) FROM Question q WHERE q.examId IN :examIds GROUP BY q.examId")
    List<Object[]> sumQuestionPointsByExamIdIn(@Param("examIds") List<Long> examIds);

    /** Libro de calificaciones — exámenes de scope COURSE para un curso específico */
    List<Exam> findByCourseIdAndAcademyIdOrderByCreatedAtAsc(Long courseId, Long academyId);

    /** Simulacros publicados vinculados a un área — siempre visibles, sin ventana horaria */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.academyId = :academyId
              AND e.status = 'PUBLISHED'
              AND e.scopeType = 'SIMULACRO'
              AND EXISTS (
                  SELECT 1 FROM ExamAreaMapping m
                  WHERE m.id.examId = e.id AND m.id.areaId = :areaId
              )
            ORDER BY e.title
            """)
    List<Exam> findSimulacrosForArea(@Param("academyId") Long academyId,
                                     @Param("areaId") Long areaId);
}

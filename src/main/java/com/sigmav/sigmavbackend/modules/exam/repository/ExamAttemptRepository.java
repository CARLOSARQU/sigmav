package com.sigmav.sigmavbackend.modules.exam.repository;

import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    List<ExamAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<ExamAttempt> findByExamIdAndStudentIdOrderByAttemptNumberDesc(Long examId, Long studentId);

    long countByExamIdAndStudentId(Long examId, Long studentId);

    /** Intentos enviados pendientes de calificación manual */
    List<ExamAttempt> findByExamIdAndStatus(Long examId, String status);

    Optional<ExamAttempt> findByIdAndStudentId(Long id, Long studentId);

    /** B8.5 — todos los intentos de un examen */
    List<ExamAttempt> findByExamId(Long examId);

    List<ExamAttempt> findByExamIdIn(List<Long> examIds);

    /** Anti-cheat — incrementa el contador de salidas de pantalla en una sola query */
    @Modifying
    @Query("UPDATE ExamAttempt a SET a.focusLostCount = a.focusLostCount + 1 WHERE a.id = :id AND a.status = 'IN_PROGRESS'")
    int incrementFocusLostCount(@Param("id") Long id);

    /**
     * Engagement — exámenes distintos intentados por cada estudiante en la academia.
     * Devuelve Object[] [studentId, distinctExamCount].
     */
    @Query(value = """
            SELECT ea.student_id, COUNT(DISTINCT ea.exam_id) AS exam_count
            FROM exam_attempts ea
            WHERE ea.academy_id = :academyId
            GROUP BY ea.student_id
            """, nativeQuery = true)
    List<Object[]> findDistinctExamCountsByAcademy(@Param("academyId") Long academyId);
}

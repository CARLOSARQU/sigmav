package com.sigmav.sigmavbackend.modules.student.repository;

import com.sigmav.sigmavbackend.modules.student.entity.StudentProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentProgressRepository extends JpaRepository<StudentProgress, Long> {

    boolean existsByStudentIdAndMaterialId(Long studentId, Long materialId);

    List<StudentProgress> findByStudentId(Long studentId);

    /** B8.6 — cuántos estudiantes vieron un material */
    long countByMaterialId(Long materialId);

    /** B8.6 — vistas de varios materiales en un solo query */
    List<StudentProgress> findByMaterialIdIn(List<Long> materialIds);

    /**
     * Engagement — cuántos materiales distintos vio cada estudiante en la academia.
     * Devuelve Object[] [studentId, viewedCount].
     */
    @Query(value = """
            SELECT sp.student_id, COUNT(DISTINCT sp.material_id) AS viewed
            FROM student_progress sp
            WHERE sp.academy_id = :academyId
            GROUP BY sp.student_id
            """, nativeQuery = true)
    List<Object[]> findMaterialViewCountsByAcademy(@Param("academyId") Long academyId);
}

package com.sigmav.sigmavbackend.modules.course.repository;

import com.sigmav.sigmavbackend.modules.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByAreaIdOrderByName(Long areaId);

    List<Course> findByAreaIdAndIsActiveTrueOrderByName(Long areaId);

    List<Course> findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(Long areaId, Long academyId);

    List<Course> findByAreaIdAndAcademyIdOrderByName(Long areaId, Long academyId);

    /** Cursos de un área a los que está asignado un docente en particular */
    @Query("SELECT c FROM Course c WHERE c.areaId = :areaId AND c.academyId = :academyId AND c.isActive = true " +
           "AND EXISTS (SELECT ct FROM CourseTeacher ct WHERE ct.courseId = c.id AND ct.teacherId = :teacherId)")
    List<Course> findByAreaIdAndAcademyIdAndTeacherId(@Param("areaId") Long areaId,
                                                      @Param("academyId") Long academyId,
                                                      @Param("teacherId") Long teacherId);

    Optional<Course> findByIdAndAcademyId(Long id, Long academyId);

    /** Cursos activos pertenecientes a una lista de áreas (usado en reporte de matrículas por ciclo) */
    List<Course> findByAreaIdInAndAcademyIdAndIsActiveTrue(List<Long> areaIds, Long academyId);

    /** Batch load de cursos por id — evita N+1 al resolver nombres en listados.
     * No filtra por isActive a propósito: una matrícula histórica de un curso
     * desactivado debe seguir mostrando su nombre. */
    @Query("SELECT c FROM Course c WHERE c.id IN :ids AND c.academyId = :academyId")
    List<Course> findByIdInAndAcademyId(@Param("ids") Collection<Long> ids,
                                        @Param("academyId") Long academyId);

    /** B8.7 — cursos activos en la academia */
    long countByAcademyIdAndIsActiveTrue(Long academyId);
}

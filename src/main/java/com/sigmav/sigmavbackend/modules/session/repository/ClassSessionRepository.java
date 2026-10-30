package com.sigmav.sigmavbackend.modules.session.repository;

import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findByCourseIdOrderBySessionDateDesc(Long courseId);

    /** Solo las sesiones del docente en un curso — para TEACHER */
    List<ClassSession> findByCourseIdAndTeacherIdOrderBySessionDateDesc(Long courseId, Long teacherId);

    Optional<ClassSession> findByQrToken(String qrToken);

    /** Para B8.2 — sesiones de un curso en rango de fechas */
    List<ClassSession> findByCourseIdAndSessionDateBetweenOrderBySessionDate(Long courseId, LocalDate from, LocalDate to);

    /** Para B8.7 — sesiones de un curso sin filtro de fecha */
    List<ClassSession> findByCourseIdAndIsActiveTrueOrderBySessionDate(Long courseId);

    Optional<ClassSession> findByIdAndAcademyId(Long id, Long academyId);

    /** Para TeacherPortal — sesiones de los cursos asignados al docente */
    List<ClassSession> findByCourseIdInAndAcademyIdAndIsActiveTrueOrderBySessionDateDescStartTimeDesc(
            List<Long> courseIds, Long academyId);

    /** Para StudentPortal — sesiones de varios cursos en rango de fechas */
    List<ClassSession> findByCourseIdInAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateDescStartTimeDesc(
            List<Long> courseIds, LocalDate from, LocalDate to);

    /** Para el Calendario admin — todas las sesiones activas de la academia en un rango de fechas */
    List<ClassSession> findByAcademyIdAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Long academyId, LocalDate from, LocalDate to);

    /** Para el Calendario docente — sesiones del teacher en un rango de fechas */
    List<ClassSession> findByTeacherIdAndAcademyIdAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Long teacherId, Long academyId, LocalDate from, LocalDate to);
}

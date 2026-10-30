package com.sigmav.sigmavbackend.modules.session.service;

import com.sigmav.sigmavbackend.modules.course.repository.CourseTeacherRepository;
import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.session.dto.*;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.ClassSessionRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassSessionService {

    private final ClassSessionRepository sessionRepository;
    private final CourseService courseService;
    private final CourseTeacherRepository courseTeacherRepository;

    public List<SessionResponse> listByCourse(Long courseId, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal); // TEACHER: solo sus cursos asignados

        List<ClassSession> sessions;
        if ("TEACHER".equals(principal.getRole())) {
            // TEACHER solo ve sus propias sesiones
            sessions = sessionRepository.findByCourseIdAndTeacherIdOrderBySessionDateDesc(courseId, principal.getId());
        } else {
            sessions = sessionRepository.findByCourseIdOrderBySessionDateDesc(courseId);
        }
        return sessions.stream().map(this::toResponse).toList();
    }

    public SessionResponse getById(Long id, UserPrincipal principal) {
        ClassSession session = findOrFail(id);
        assertSessionAccess(session, principal);
        return toResponse(session);
    }

    @Transactional
    public SessionResponse create(Long courseId, CreateSessionRequest request, UserPrincipal principal) {
        courseService.findOrFail(courseId);
        courseService.assertCourseAccess(courseId, principal);

        Long teacherId = resolveTeacherId(request.teacherId(), principal);

        String mode = request.attendanceMode() != null
                ? request.attendanceMode().toUpperCase() : "NONE";
        validateAttendanceMode(mode);

        ClassSession session = ClassSession.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .courseId(courseId)
                .teacherId(teacherId)
                .title(request.title())
                .sessionDate(request.sessionDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .attendanceMode(mode)
                .qrToken("QR".equals(mode) ? UUID.randomUUID().toString() : null)
                .attendanceOpenFrom(request.attendanceOpenFrom())
                .attendanceOpenUntil(request.attendanceOpenUntil())
                .attendanceTolerance(request.attendanceTolerance())
                .meetingUrl(request.meetingUrl())
                .build();

        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse update(Long id, UpdateSessionRequest request, UserPrincipal principal) {
        ClassSession session = findOrFail(id);
        assertSessionAccess(session, principal);

        if (request.title() != null) session.setTitle(request.title());
        if (request.sessionDate() != null) session.setSessionDate(request.sessionDate());
        if (request.startTime() != null) session.setStartTime(request.startTime());
        if (request.endTime() != null) session.setEndTime(request.endTime());
        if (request.attendanceOpenFrom() != null) session.setAttendanceOpenFrom(request.attendanceOpenFrom());
        if (request.attendanceOpenUntil() != null) session.setAttendanceOpenUntil(request.attendanceOpenUntil());
        if (request.attendanceTolerance() != null) session.setAttendanceTolerance(request.attendanceTolerance());
        if (request.isActive() != null) session.setIsActive(request.isActive());
        session.setMeetingUrl(request.meetingUrl());

        if (request.attendanceMode() != null) {
            String mode = request.attendanceMode().toUpperCase();
            validateAttendanceMode(mode);
            session.setAttendanceMode(mode);
            // Si cambia a QR y no tenía token, generar uno nuevo
            if ("QR".equals(mode) && session.getQrToken() == null) {
                session.setQrToken(UUID.randomUUID().toString());
            }
        }

        return toResponse(sessionRepository.save(session));
    }

    /** B6.3 — Devuelve el token QR de una sesión (solo si mode=QR) */
    public QrTokenResponse getQrToken(Long id, UserPrincipal principal) {
        ClassSession session = findOrFail(id);
        assertSessionAccess(session, principal);

        if (!"QR".equals(session.getAttendanceMode())) {
            throw new IllegalArgumentException("Esta sesión no tiene modo QR habilitado");
        }
        if (session.getQrToken() == null) {
            throw new IllegalArgumentException("Esta sesión no tiene QR generado");
        }

        return new QrTokenResponse(
                session.getId(), session.getQrToken(),
                session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                session.getAttendanceOpenFrom(), session.getAttendanceOpenUntil()
        );
    }

    /** TeacherPortal — sesiones de los cursos asignados al docente */
    public List<SessionResponse> listByTeacher(Long teacherId) {
        List<Long> courseIds = courseTeacherRepository.findCourseIdsByTeacherId(teacherId);
        if (courseIds.isEmpty()) return List.of();
        return sessionRepository
                .findByCourseIdInAndAcademyIdAndIsActiveTrueOrderBySessionDateDescStartTimeDesc(
                        courseIds, TenantContext.getCurrentAcademyId())
                .stream().map(this::toResponse).toList();
    }

    // --- Helpers ---

    public ClassSession findOrFail(Long id) {
        return sessionRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Sesión no encontrada con ID: " + id));
    }

    /**
     * B6.2 — TEACHER solo puede gestionar sus propias sesiones.
     * ACADEMY_ADMIN puede gestionar cualquier sesión.
     */
    public void assertSessionAccess(ClassSession session, UserPrincipal principal) {
        if ("TEACHER".equals(principal.getRole())
                && !session.getTeacherId().equals(principal.getId())) {
            throw new AccessDeniedException("No tiene acceso a esta sesión");
        }
    }

    private Long resolveTeacherId(Long requestTeacherId, UserPrincipal principal) {
        if ("TEACHER".equals(principal.getRole())) {
            return principal.getId(); // TEACHER siempre es el propietario de su sesión
        }
        // ACADEMY_ADMIN debe especificar el teacherId
        if (requestTeacherId == null) {
            throw new IllegalArgumentException("teacherId es requerido al crear una sesión como administrador");
        }
        return requestTeacherId;
    }

    private void validateAttendanceMode(String mode) {
        if (!mode.equals("NONE") && !mode.equals("BUTTON") && !mode.equals("QR")) {
            throw new IllegalArgumentException("Modo de asistencia inválido: " + mode + ". Use NONE, BUTTON o QR");
        }
    }

    private SessionResponse toResponse(ClassSession s) {
        return new SessionResponse(s.getId(), s.getUuid(), s.getCourseId(), s.getTeacherId(),
                s.getTitle(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getAttendanceMode(), s.getQrToken(), s.getAttendanceOpenFrom(),
                s.getAttendanceOpenUntil(), s.getAttendanceTolerance(), s.getIsActive(),
                s.getCreatedAt(), s.getMeetingUrl());
    }
}

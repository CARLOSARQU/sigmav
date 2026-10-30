package com.sigmav.sigmavbackend.modules.session.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.enrollment.service.EnrollmentService;
import com.sigmav.sigmavbackend.modules.session.dto.AttendanceListItemResponse;
import com.sigmav.sigmavbackend.modules.session.dto.UpdateAttendanceRequest;
import com.sigmav.sigmavbackend.modules.session.entity.AttendanceRecord;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.StudentPortalService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRepository;
    private final ClassSessionService sessionService;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentPortalService studentPortalService;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final AcademyRepository academyRepository;

    // Constantes para SonarQube
    private static final String STATUS_PRESENT = "PRESENT";
    private static final String STATUS_LATE = "LATE";
    private static final String STATUS_ABSENT = "ABSENT";
    private static final String METHOD_BUTTON = "BUTTON";
    private static final String METHOD_QR = "QR";
    private static final String METHOD_MANUAL = "MANUAL";

    /**
     * B6.4 — Marcar asistencia en modo BUTTON.
     * Solo estudiantes con matrícula activa y dentro de la ventana de tiempo.
     */
    @Transactional
    public void markButtonAttendance(Long sessionId, UserPrincipal principal) {
        ClassSession session = sessionService.findOrFail(sessionId);
        Student student = studentPortalService.resolveStudent(principal);

        enrollmentService.assertStudentAccess(student.getId(), session.getCourseId());

        if (!METHOD_BUTTON.equals(session.getAttendanceMode())) {
            throw new IllegalArgumentException("Esta sesión no tiene modo BUTTON habilitado");
        }

        validateTimeWindow(session);

        if (attendanceRepository.existsBySessionIdAndStudentId(sessionId, student.getId())) {
            throw new IllegalArgumentException("Ya registraste tu asistencia en esta sesión");
        }

        String status = computeStatus(session, LocalDateTime.now(), session.getAcademyId());

        // Corrección SonarQube: Renombrado de 'record' a 'attendanceRecord'
        AttendanceRecord attendanceRecord = AttendanceRecord.builder()
                .academyId(session.getAcademyId())
                .sessionId(sessionId)
                .studentId(student.getId())
                .checkInAt(LocalDateTime.now())
                .method(METHOD_BUTTON)
                .status(status)
                .build();

        attendanceRepository.save(attendanceRecord);
    }

    /**
     * B6.5 — Check-in QR.
     */
    @Transactional
    public void checkIn(Long sessionId, String qrToken, UserPrincipal principal) {
        ClassSession session = sessionService.findOrFail(sessionId);
        Student student = studentPortalService.resolveStudent(principal);

        // Validar matrícula ANTES del QR — mensaje correcto si no está matriculado
        enrollmentService.assertStudentAccess(student.getId(), session.getCourseId());

        // Validar modo QR y token
        if (!METHOD_QR.equals(session.getAttendanceMode())) {
            throw new IllegalArgumentException("Esta sesión no tiene modo QR habilitado");
        }
        if (!qrToken.equals(session.getQrToken())) {
            throw new AccessDeniedException("Token QR inválido");
        }
        validateTimeWindow(session);

        if (attendanceRepository.existsBySessionIdAndStudentId(sessionId, student.getId())) {
            throw new IllegalArgumentException("Ya registraste tu entrada en esta sesión");
        }

        String status = computeStatus(session, LocalDateTime.now(), session.getAcademyId());

        AttendanceRecord attendanceRecord = AttendanceRecord.builder()
                .academyId(session.getAcademyId())
                .sessionId(sessionId)
                .studentId(student.getId())
                .checkInAt(LocalDateTime.now())
                .method(METHOD_QR)
                .status(status)
                .build();

        attendanceRepository.save(attendanceRecord);
    }

    /**
     * B6.6 — Check-out QR.
     */
    @Transactional
    public void checkOut(Long sessionId, String qrToken, UserPrincipal principal) {
        ClassSession session = sessionService.findOrFail(sessionId);
        Student student = studentPortalService.resolveStudent(principal);

        // Validar matrícula y modo QR
        enrollmentService.assertStudentAccess(student.getId(), session.getCourseId());
        if (!METHOD_QR.equals(session.getAttendanceMode())) {
            throw new IllegalArgumentException("Esta sesión no tiene modo QR habilitado");
        }
        if (!qrToken.equals(session.getQrToken())) {
            throw new AccessDeniedException("Token QR inválido");
        }
        validateTimeWindow(session);

        AttendanceRecord attendanceRecord = attendanceRepository
                .findBySessionIdAndStudentId(sessionId, student.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Debes registrar tu entrada antes de registrar la salida"));

        if (attendanceRecord.getCheckOutAt() != null) {
            throw new IllegalArgumentException("Ya registraste tu salida en esta sesión");
        }

        attendanceRecord.setCheckOutAt(LocalDateTime.now());
        attendanceRepository.save(attendanceRecord);
    }

    /**
     * B6.7 — Lista de asistencia con todos los estudiantes matriculados y su estado.
     */
    public List<AttendanceListItemResponse> getAttendanceList(Long sessionId, UserPrincipal principal) {
        ClassSession session = sessionService.findOrFail(sessionId);
        sessionService.assertSessionAccess(session, principal);

        Course course = courseRepository.findById(session.getCourseId())
                .orElseThrow(() -> new NotFoundException("Curso no encontrado"));

        List<Long> enrolledIds = enrollmentRepository.findEnrolledStudentIds(
                session.getCourseId(), course.getAreaId());

        List<Student> students = studentRepository.findAllById(enrolledIds);

        Map<Long, AttendanceRecord> recordsByStudentId = attendanceRepository
                .findBySessionId(sessionId)
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));

        List<AttendanceListItemResponse> result = new ArrayList<>();
        for (Student student : students) {
            AttendanceRecord rec = recordsByStudentId.get(student.getId());
            if (rec != null) {
                result.add(new AttendanceListItemResponse(
                        student.getId(), student.getFullName(),
                        rec.getStatus(), rec.getCheckInAt(), rec.getCheckOutAt(), rec.getMethod()));
            } else {
                // Sin registro = pendiente (la sesión aún no terminó o el estudiante no ha marcado)
                result.add(new AttendanceListItemResponse(
                        student.getId(), student.getFullName(),
                        null, null, null, null));
            }
        }
        return result;
    }

    /**
     * B6.7 — Corrección manual del estado de asistencia.
     */
    @Transactional
    public void updateAttendanceStatus(Long sessionId, Long studentId,
                                       UpdateAttendanceRequest request, UserPrincipal principal) {
        ClassSession session = sessionService.findOrFail(sessionId);
        sessionService.assertSessionAccess(session, principal);

        String status = request.status().toUpperCase();
        if (!status.equals(STATUS_PRESENT) && !status.equals(STATUS_LATE) && !status.equals(STATUS_ABSENT)) {
            throw new IllegalArgumentException("Estado inválido: " + status + ". Use PRESENT, LATE o ABSENT");
        }

        Optional<AttendanceRecord> existing = attendanceRepository
                .findBySessionIdAndStudentId(sessionId, studentId);

        if (existing.isPresent()) {
            existing.get().setStatus(status);
            existing.get().setMethod(METHOD_MANUAL);
            attendanceRepository.save(existing.get());
        } else {
            // Crear registro manual si no existe
            attendanceRepository.save(AttendanceRecord.builder()
                    .academyId(session.getAcademyId())
                    .sessionId(sessionId)
                    .studentId(studentId)
                    .method(METHOD_MANUAL)
                    .status(status)
                    .build());
        }
    }

    // --- Helpers ---

    /**
     * B6.8 — Calcula el estado según la hora de check-in vs tolerancia.
     * Tolerancia por sesión > tolerancia global de academia (config JSONB) > default 10 min.
     */
    private String computeStatus(ClassSession session, LocalDateTime checkInAt, Long academyId) {
        if (session.getAttendanceOpenFrom() == null) {
            return STATUS_PRESENT;
        }
        int toleranceMinutes = getToleranceMinutes(session, academyId);
        LocalDateTime lateThreshold = session.getAttendanceOpenFrom().plusMinutes(toleranceMinutes);
        return checkInAt.isAfter(lateThreshold) ? STATUS_LATE : STATUS_PRESENT;
    }

    /** Resuelve tolerancia en orden: sesión → academia config → default 10 min */
    private int getToleranceMinutes(ClassSession session, Long academyId) {
        if (session.getAttendanceTolerance() != null) {
            return session.getAttendanceTolerance();
        }
        // Una sola query a academia, maneja String/Number/null del JSONB
        Academy academy = academyRepository.findById(academyId).orElse(null);
        if (academy != null && academy.getConfig() != null) {
            Object raw = academy.getConfig().get("attendanceTolerance");
            if (raw instanceof Number n) return n.intValue();
            if (raw instanceof String s) {
                try { return Integer.parseInt(s); } catch (NumberFormatException e) { /* fall through */ }
            }
        }
        return 10;
    }

    /** Valida que NOW() esté dentro de la ventana de asistencia */
    private void validateTimeWindow(ClassSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getAttendanceOpenFrom() != null && now.isBefore(session.getAttendanceOpenFrom())) {
            throw new AccessDeniedException("La ventana de asistencia aún no ha comenzado");
        }
        if (session.getAttendanceOpenUntil() != null && now.isAfter(session.getAttendanceOpenUntil())) {
            throw new AccessDeniedException("La ventana de asistencia ya cerró");
        }
    }

    /** Valida token QR y modo QR */
    private ClassSession validateQrRequest(Long sessionId, String qrToken) {
        ClassSession session = sessionService.findOrFail(sessionId);

        if (!METHOD_QR.equals(session.getAttendanceMode())) {
            throw new IllegalArgumentException("Esta sesión no tiene modo QR habilitado");
        }
        if (!qrToken.equals(session.getQrToken())) {
            throw new AccessDeniedException("Token QR inválido");
        }
        return session;
    }
}
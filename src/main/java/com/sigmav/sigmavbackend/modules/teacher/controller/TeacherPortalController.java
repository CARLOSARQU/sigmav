package com.sigmav.sigmavbackend.modules.teacher.controller;

import com.sigmav.sigmavbackend.modules.admin.dto.ChangePasswordRequest;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseTeacherRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.teacher.dto.TeacherStudentView;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.course.service.CourseService;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.sigmav.sigmavbackend.modules.session.dto.SessionResponse;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.ClassSessionRepository;
import com.sigmav.sigmavbackend.modules.session.service.ClassSessionService;
import com.sigmav.sigmavbackend.modules.student.dto.StudentCalendarResponse;
import com.sigmav.sigmavbackend.modules.teacher.dto.CourseTeacherResponse;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Portal del docente — endpoints exclusivos para el rol TEACHER.
 * Expone los cursos asignados y las sesiones propias del docente autenticado.
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherPortalController {

    private final CourseService courseService;
    private final ClassSessionService sessionService;
    private final ClassSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final StorageService storageService;

    /** Lista los cursos asignados al docente autenticado */
    @GetMapping("/courses")
    public ResponseEntity<List<CourseTeacherResponse>> getMyCourses(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(courseService.listByTeacher(principal.getId()));
    }

    /** Lista todas las sesiones del docente autenticado (agenda unificada) */
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getMySessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.listByTeacher(principal.getId()));
    }

    /** Calendario del docente — sesiones en rango + exámenes publicados */
    @GetMapping("/calendar")
    public ResponseEntity<StudentCalendarResponse> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long academyId = TenantContext.getCurrentAcademyId();

        // Solo cursos asignados al docente
        List<Long> courseIds = courseTeacherRepository.findCourseIdsByTeacherId(principal.getId());

        // Sesiones de esos cursos en el rango
        List<ClassSession> classSessions = courseIds.isEmpty() ? List.of() :
                sessionRepository
                        .findByCourseIdInAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateDescStartTimeDesc(
                                courseIds, from, to);
        Map<Long, String> courseNames = courseRepository.findAllById(courseIds)
                .stream().collect(Collectors.toMap(Course::getId, Course::getName));

        List<StudentCalendarResponse.SessionEvent> sessions = classSessions.stream()
                .map(s -> new StudentCalendarResponse.SessionEvent(
                        s.getId(), s.getTitle(),
                        courseNames.getOrDefault(s.getCourseId(), "Curso #" + s.getCourseId()),
                        s.getSessionDate(), s.getStartTime(), s.getEndTime(), s.getMeetingUrl()
                )).toList();

        // Exámenes publicados de la academia (visibles para el docente)
        List<StudentCalendarResponse.ExamEvent> exams = examRepository
                .findAllPublishedForStudent(academyId, LocalTime.now())
                .stream()
                .map(e -> new StudentCalendarResponse.ExamEvent(
                        e.getId(), e.getTitle(), e.getScopeType(),
                        e.getDurationMinutes(), e.getAvailableFrom(), e.getAvailableUntil()
                )).toList();

        return ResponseEntity.ok(new StudentCalendarResponse(sessions, exams));
    }

    /** Lista de estudiantes matriculados en un curso asignado al docente */
    @GetMapping("/courses/{courseId}/students")
    public ResponseEntity<List<TeacherStudentView>> getCourseStudents(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {

        // Valida que el docente tenga acceso al curso
        courseService.assertCourseAccess(courseId, principal);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Curso no encontrado"));

        // Obtiene IDs de estudiantes matriculados (por curso directo o por área)
        List<Long> studentIds = enrollmentRepository.findEnrolledStudentIds(courseId, course.getAreaId());

        if (studentIds.isEmpty()) return ResponseEntity.ok(List.of());

        List<TeacherStudentView> result = studentRepository.findAllById(studentIds)
                .stream()
                .sorted((a, b) -> {
                    int last = a.getLastName().compareToIgnoreCase(b.getLastName());
                    return last != 0 ? last : a.getFirstName().compareToIgnoreCase(b.getFirstName());
                })
                .map(s -> new TeacherStudentView(
                        s.getId(),
                        s.getFirstName() + " " + s.getLastName(),
                        s.getDni(),
                        s.getEmail(),
                        s.getPhone(),
                        s.getIsActive()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    /** Datos del docente autenticado (incluye avatarUrl) */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        return ResponseEntity.ok(result);
    }

    /** Subir foto de perfil del docente autenticado */
    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, Object>> uploadMyAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        try {
            String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                    : ".jpg";
            String key = "avatars/teachers/" + user.getUuid() + ext;
            String url = storageService.uploadLogo(key, file.getBytes(), file.getContentType());
            user.setAvatarUrl(url);
            userRepository.save(user);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("avatarUrl", url);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al subir la imagen", e);
        }
    }

    /** Cambio de contraseña del docente autenticado */
    @PostMapping("/profile/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no es correcta");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}

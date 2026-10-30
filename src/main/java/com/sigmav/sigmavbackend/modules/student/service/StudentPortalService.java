package com.sigmav.sigmavbackend.modules.student.service;

import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.entity.CourseMaterial;
import com.sigmav.sigmavbackend.modules.course.repository.CourseMaterialRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.cycle.entity.Cycle;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.enrollment.service.EnrollmentService;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.dto.QuestionResponse;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.modules.session.entity.AttendanceRecord;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.session.repository.ClassSessionRepository;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.dto.StudentCalendarResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentCourseResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentMaterialResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentSessionResponse;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.entity.StudentProgress;
import com.sigmav.sigmavbackend.modules.student.repository.StudentProgressRepository;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class StudentPortalService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final CourseRepository courseRepository;
    private final AreaRepository areaRepository;
    private final CycleRepository cycleRepository;
    private final CourseMaterialRepository materialRepository;
    private final StudentProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;

    /**
     * B5.6 — Lista los cursos del estudiante desde sus matrículas activas y no vencidas.
     * Incluye jerarquía ciclo → área → curso.
     */
    public List<StudentCourseResponse> getMyCourses(UserPrincipal principal) {
        Student student = resolveStudent(principal);
        Long academyId = TenantContext.getCurrentAcademyId();

        List<Enrollment> areaEnrollments = enrollmentRepository.findActiveAreaEnrollments(student.getId());
        List<Enrollment> courseEnrollments = enrollmentRepository.findActiveCourseEnrollments(student.getId());

        List<Course> allCourses = new ArrayList<>();
        Map<Long, String> enrollmentTypeByCourseId = new LinkedHashMap<>();

        for (Enrollment e : areaEnrollments) {
            List<Course> courses = courseRepository.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(
                    e.getAreaId(), academyId);
            for (Course c : courses) {
                allCourses.add(c);
                enrollmentTypeByCourseId.put(c.getId(), e.getEnrollmentType());
            }
        }

        List<Long> directCourseIds = courseEnrollments.stream()
                .map(Enrollment::getCourseId).toList();
        Map<Long, Course> directCourseMap = courseRepository.findAllById(directCourseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        for (Enrollment e : courseEnrollments) {
            Course c = directCourseMap.get(e.getCourseId());
            if (c != null) {
                allCourses.add(c);
                enrollmentTypeByCourseId.put(c.getId(), e.getEnrollmentType());
            }
        }

        if (allCourses.isEmpty()) return List.of();

        Set<Long> areaIds = allCourses.stream().map(Course::getAreaId).collect(Collectors.toSet());
        Map<Long, Area> areaMap = areaRepository.findAllById(areaIds).stream()
                .collect(Collectors.toMap(Area::getId, a -> a));
        Set<Long> cycleIds = areaMap.values().stream().map(Area::getCycleId).collect(Collectors.toSet());
        Map<Long, Cycle> cycleMap = cycleRepository.findAllById(cycleIds).stream()
                .collect(Collectors.toMap(Cycle::getId, c -> c));

        return allCourses.stream()
                .map(course -> {
                    Area area = areaMap.get(course.getAreaId());
                    Cycle cycle = area != null ? cycleMap.get(area.getCycleId()) : null;
                    String enrollmentType = enrollmentTypeByCourseId.get(course.getId());
                    return new StudentCourseResponse(
                            course.getId(), course.getUuid(), course.getName(), course.getDescription(),
                            area  != null ? area.getId()         : null,
                            area  != null ? area.getName()       : null,
                            cycle != null ? cycle.getId()        : null,
                            cycle != null ? cycle.getName()      : null,
                            cycle != null ? cycle.getStartDate() : null,
                            cycle != null ? cycle.getEndDate()   : null,
                            enrollmentType
                    );
                })
                .toList();
    }

    /**
     * B5.7 — Lista materiales activos de un curso para el estudiante.
     * Valida acceso (B5.5) e indica cuáles ya fueron vistos sin marcarlos automáticamente.
     */
    public List<StudentMaterialResponse> getMaterials(Long courseId, UserPrincipal principal) {
        Student student = resolveStudent(principal);
        enrollmentService.assertStudentAccess(student.getId(), courseId);

        List<CourseMaterial> materials = materialRepository.findByCourseIdAndIsActiveTrueOrderByOrderIndex(courseId);

        Set<Long> viewedMaterialIds = progressRepository.findByStudentId(student.getId())
                .stream().map(StudentProgress::getMaterialId).collect(Collectors.toSet());

        return materials.stream()
                .map(m -> toStudentMaterialResponse(m, viewedMaterialIds.contains(m.getId())))
                .toList();
    }

    /**
     * Marca un material como visto por el estudiante.
     * Solo inserta el registro si no existía previamente (idempotente).
     */
    @Transactional
    public void markViewed(String uuid, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Student student = resolveStudent(principal);

        CourseMaterial material = materialRepository
                .findByUuidAndAcademyId(UUID.fromString(uuid), academyId)
                .orElseThrow(() -> new NotFoundException("Material no encontrado"));

        enrollmentService.assertStudentAccess(student.getId(), material.getCourseId());

        if (!progressRepository.existsByStudentIdAndMaterialId(student.getId(), material.getId())) {
            progressRepository.save(StudentProgress.builder()
                    .studentId(student.getId())
                    .materialId(material.getId())
                    .academyId(TenantContext.getCurrentAcademyId())
                    .build());
        }
    }

    /**
     * Sesiones de los cursos matriculados — últimos 7 días y próximos 30 días.
     * Incluye el estado de asistencia del propio estudiante para cada sesión.
     */
    public List<StudentSessionResponse> getMySessions(UserPrincipal principal) {
        Student student = resolveStudent(principal);

        // Obtener IDs de todos los cursos activos del estudiante
        List<Long> courseIds = getMyCourseIds(student);
        if (courseIds.isEmpty()) return List.of();

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to   = LocalDate.now().plusDays(30);

        List<ClassSession> sessions = sessionRepository
                .findByCourseIdInAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateDescStartTimeDesc(
                        courseIds, from, to);

        if (sessions.isEmpty()) return List.of();

        // Obtener todos los registros de asistencia del estudiante de una sola vez
        List<Long> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        Map<Long, AttendanceRecord> attendanceBySessionId = attendanceRepository
                .findBySessionIdIn(sessionIds)
                .stream()
                .filter(r -> r.getStudentId().equals(student.getId()))
                .collect(Collectors.toMap(AttendanceRecord::getSessionId, r -> r));

        // Mapa courseId → nombre para no hacer N queries
        Map<Long, String> courseNames = courseRepository.findAllById(courseIds)
                .stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));

        return sessions.stream().map(s -> {
            AttendanceRecord attendanceEntry = attendanceBySessionId.get(s.getId());
            boolean attended = attendanceEntry != null;
            String status    = attended ? attendanceEntry.getStatus() : null;
            String courseName = courseNames.getOrDefault(s.getCourseId(), "Curso #" + s.getCourseId());

            return new StudentSessionResponse(
                    s.getId(), s.getCourseId(), courseName, s.getTitle(),
                    s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                    s.getAttendanceMode(), s.getAttendanceOpenFrom(), s.getAttendanceOpenUntil(),
                    attended, status, s.getMeetingUrl()
            );
        }).toList();
    }

    /**
     * B5.8 — El estudiante solo puede cambiar su email y contraseña.
     */
    @Transactional
    public void updateProfile(UserPrincipal principal, String newPassword) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        if (newPassword != null && !newPassword.isBlank())
            user.setPasswordHash(passwordEncoder.encode(newPassword));

        userRepository.save(user);
    }

    /**
     * Genera una URL de descarga prefirmada para un material PDF_S3.
     * Valida que el estudiante tiene matrícula activa en el curso del material.
     */
    public DownloadUrlResponse generateMaterialDownloadUrl(String uuid, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Student student = resolveStudent(principal);

        CourseMaterial material = materialRepository
                .findByUuidAndAcademyId(UUID.fromString(uuid), academyId)
                .orElseThrow(() -> new NotFoundException("Material no encontrado"));

        if (!"PDF_S3".equals(material.getMaterialType())) {
            throw new IllegalArgumentException("Este material no tiene archivo privado en R2");
        }

        enrollmentService.assertStudentAccess(student.getId(), material.getCourseId());

        String url = storageService.generateDownloadUrl(material.getFileUrl());
        return new DownloadUrlResponse(url, storageService.getExpiryMinutes() * 60);
    }

    /**
     * Devuelve las preguntas de un intento en curso SIN revelar las respuestas correctas.
     * Solo accesible por el estudiante dueño del intento y mientras esté IN_PROGRESS.
     */
    public List<QuestionResponse> getAttemptQuestions(Long attemptId, UserPrincipal principal) {
        Student student = resolveStudent(principal);

        ExamAttempt attempt = examAttemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException("Intento no encontrado"));

        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new IllegalStateException("Solo se pueden ver preguntas de intentos en curso");
        }

        return questionRepository.findByExamIdOrderByOrderIndex(attempt.getExamId())
                .stream()
                .map(q -> new QuestionResponse(
                        q.getId(), q.getUuid(), q.getExamId(),
                        q.getQuestionText(), q.getQuestionType(),
                        q.getOptions(),
                        null,   // correctAnswer oculta durante el examen
                        q.getPoints(), q.getOrderIndex(), q.getRequiresManualGrading()
                ))
                .toList();
    }

    /**
     * Calendario del estudiante:
     *  - Sesiones de sus cursos matriculados en el rango from–to
     *  - Exámenes publicados activos en ventana horaria (sin fecha, panel aparte)
     */
    public StudentCalendarResponse getCalendarData(LocalDate from, LocalDate to, UserPrincipal principal) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Student student = resolveStudent(principal);

        List<Long> courseIds = getMyCourseIds(student);

        // Sesiones en el rango
        List<StudentCalendarResponse.SessionEvent> sessions;
        if (courseIds.isEmpty()) {
            sessions = List.of();
        } else {
            Map<Long, String> courseNames = courseRepository.findAllById(courseIds)
                    .stream()
                    .collect(Collectors.toMap(Course::getId, Course::getName));

            sessions = sessionRepository
                    .findByCourseIdInAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateDescStartTimeDesc(
                            courseIds, from, to)
                    .stream()
                    .map(s -> new StudentCalendarResponse.SessionEvent(
                            s.getId(),
                            s.getTitle(),
                            courseNames.getOrDefault(s.getCourseId(), "Curso #" + s.getCourseId()),
                            s.getSessionDate(),
                            s.getStartTime(),
                            s.getEndTime(),
                            s.getMeetingUrl()
                    ))
                    .toList();
        }

        // Exámenes publicados — filtrados por los cursos/áreas/ciclos del estudiante
        List<StudentCalendarResponse.ExamEvent> exams;
        if (courseIds.isEmpty()) {
            exams = List.of();
        } else {
            // Derivar areaIds y cycleIds desde los cursos matriculados
            List<Long> areaIds = courseRepository.findAllById(courseIds)
                    .stream().map(Course::getAreaId).distinct().toList();
            List<Long> cycleIds = areaRepository.findAllById(areaIds)
                    .stream().map(a -> a.getCycleId()).distinct().toList();

            exams = examRepository
                    .findAllPublishedForStudent(academyId, java.time.LocalTime.now())
                    .stream()
                    .filter(e -> switch (e.getScopeType()) {
                        case "COURSE" -> e.getCourseId() != null && courseIds.contains(e.getCourseId());
                        case "AREA"   -> e.getAreaId()   != null && areaIds.contains(e.getAreaId());
                        case "CYCLE"  -> e.getCycleId()  != null && cycleIds.contains(e.getCycleId());
                        default -> false;
                    })
                    .map(e -> new StudentCalendarResponse.ExamEvent(
                            e.getId(), e.getTitle(), e.getScopeType(),
                            e.getDurationMinutes(), e.getAvailableFrom(), e.getAvailableUntil()
                    ))
                    .toList();
        }

        return new StudentCalendarResponse(sessions, exams);
    }

    // --- Helpers ---

    public Student resolveStudent(UserPrincipal principal) {
        return studentRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new NotFoundException("Perfil de estudiante no encontrado"));
    }

    private List<Long> getMyCourseIds(Student student) {
        List<Long> ids = new ArrayList<>();

        enrollmentRepository.findActiveAreaEnrollments(student.getId()).forEach(enrollment ->
                courseRepository.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(
                        enrollment.getAreaId(), TenantContext.getCurrentAcademyId())
                        .forEach(c -> ids.add(c.getId()))
        );

        enrollmentRepository.findActiveCourseEnrollments(student.getId())
                .forEach(enrollment -> ids.add(enrollment.getCourseId()));

        return ids.stream().distinct().toList();
    }

    private StudentCourseResponse buildCourseResponse(Course course, String enrollmentType) {
        Area area   = areaRepository.findById(course.getAreaId()).orElse(null);
        Cycle cycle = (area != null) ? cycleRepository.findById(area.getCycleId()).orElse(null) : null;

        return new StudentCourseResponse(
                course.getId(), course.getUuid(), course.getName(), course.getDescription(),
                area  != null ? area.getId()            : null,
                area  != null ? area.getName()          : null,
                cycle != null ? cycle.getId()           : null,
                cycle != null ? cycle.getName()         : null,
                cycle != null ? cycle.getStartDate()    : null,
                cycle != null ? cycle.getEndDate()      : null,
                enrollmentType
        );
    }

    private StudentMaterialResponse toStudentMaterialResponse(CourseMaterial m, boolean viewed) {
        return new StudentMaterialResponse(m.getId(), m.getUuid(), m.getCourseId(), m.getSectionId(),
                m.getTitle(), m.getMaterialType(), m.getFileUrl(), m.getOrderIndex(),
                m.getIsActive(), m.getCreatedAt(), viewed);
    }

    public com.sigmav.sigmavbackend.modules.student.dto.StudentResponse getMyProfile(UserPrincipal principal) {
        Student student = studentRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new NotFoundException("Perfil de estudiante no encontrado"));
        boolean hasActive = enrollmentRepository.existsActiveEnrollmentForStudent(student.getId());
        return toStudentResponse(student, hasActive);
    }

    @Transactional
    public com.sigmav.sigmavbackend.modules.student.dto.StudentResponse uploadMyAvatar(
            org.springframework.web.multipart.MultipartFile file,
            UserPrincipal principal,
            com.sigmav.sigmavbackend.modules.storage.service.StorageService storageService) {
        Student student = studentRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new NotFoundException("Perfil de estudiante no encontrado"));
        try {
            String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                    ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                    : ".jpg";
            String key = "avatars/students/" + student.getUuid() + ext;
            String url = storageService.uploadLogo(key, file.getBytes(), file.getContentType());
            student.setAvatarUrl(url);
            studentRepository.save(student);
        } catch (Exception e) {
            throw new IllegalStateException("Error al subir la imagen: " + e.getMessage());
        }
        boolean hasActive = enrollmentRepository.existsActiveEnrollmentForStudent(student.getId());
        return toStudentResponse(student, hasActive);
    }

    private com.sigmav.sigmavbackend.modules.student.dto.StudentResponse toStudentResponse(Student s, boolean hasActive) {
        return new com.sigmav.sigmavbackend.modules.student.dto.StudentResponse(
                s.getId(), s.getUuid(), s.getAcademyId(), s.getUserId(),
                s.getFirstName(), s.getLastName(), s.getFullName(),
                s.getDni(), s.getEmail(), s.getPhone(), s.getCode(),
                s.getAvatarUrl(), s.getIsActive(), s.getCreatedAt(), hasActive, null, List.of());
    }
}

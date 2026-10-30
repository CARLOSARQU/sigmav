package com.sigmav.sigmavbackend.modules.exam.service;

import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.dto.AttemptResponse;
import com.sigmav.sigmavbackend.modules.exam.dto.ExamSummaryResponse;
import com.sigmav.sigmavbackend.modules.exam.dto.StudentResultResponse;
import com.sigmav.sigmavbackend.modules.exam.dto.SubmitAnswersRequest;
import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.exam.entity.Question;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.dto.UploadUrlResponse;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.StudentPortalService;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAttemptService {

    private static final String REDIS_KEY_PREFIX = "exam_attempt:";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String ATTEMPT_NOT_FOUND = "Intento no encontrado: ";

    private final ExamAttemptRepository attemptRepository;
    private final ExamRepository examRepository;
    private final ExamService examService;
    private final GradingService gradingService;
    private final StudentPortalService studentPortalService;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AreaRepository areaRepository;
    private final CycleRepository cycleRepository;
    private final StringRedisTemplate redisTemplate;
    private final StorageService storageService;
    private final StudentRepository studentRepository;
    private final QuestionRepository questionRepository;

    // --- Portal del estudiante ---

    /** B7.5 + B7.6 — Iniciar intento de examen */
    @Transactional
    public AttemptResponse startAttempt(Long examId, UserPrincipal principal) {
        Exam exam = examService.findOrFail(examId);
        Student student = studentPortalService.resolveStudent(principal);

        // Examen debe estar publicado
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new AccessDeniedException("El examen no está disponible");
        }

        // B7.5 — Validar ventana de horario
        validateSchedule(exam);

        // Verificar acceso del estudiante por scope
        assertStudentExamAccess(student.getId(), exam);

        // Verificar intentos disponibles
        long usedAttempts = attemptRepository.countByExamIdAndStudentId(examId, student.getId());
        if (usedAttempts >= exam.getMaxAttempts()) {
            throw new AccessDeniedException("Has agotado los intentos disponibles para este examen (" + exam.getMaxAttempts() + ")");
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .examId(examId)
                .studentId(student.getId())
                .attemptNumber((int) usedAttempts + 1)
                .build();

        attempt = attemptRepository.save(attempt);

        // B7.7 — Timer en Redis: TTL = durationMinutes * 60 segundos
        String redisKey = REDIS_KEY_PREFIX + attempt.getId();
        redisTemplate.opsForValue().set(redisKey, "active",
                Duration.ofSeconds((long) exam.getDurationMinutes() * 60));

        return toAttemptResponse(attempt, (long) exam.getDurationMinutes() * 60);
    }

    /** B7.7 — Tiempo restante del intento (desde Redis TTL) */
    public AttemptResponse getAttempt(Long attemptId, UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        ExamAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));

        Long remaining = null;
        if ("IN_PROGRESS".equals(attempt.getStatus())) {
            remaining = resolveRemainingSeconds(attempt);
            if (remaining <= 0) {
                // Tiempo agotado → auto-submit
                autoSubmitExpired(attempt);
                remaining = 0L;
            }
        }
        return toAttemptResponse(attempt, remaining);
    }

    /** B7.8 — Enviar respuestas */
    @Transactional
    public AttemptResponse submit(Long attemptId, SubmitAnswersRequest request, UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        ExamAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));

        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new IllegalArgumentException("Este intento ya fue enviado o calificado");
        }

        // Guardar respuestas en estructura estándar
        Map<String, Object> data = new HashMap<>();
        data.put("answers", request.answers());
        attempt.setAnswers(data);
        attempt.setStatus(STATUS_SUBMITTED);
        attempt.setSubmittedAt(java.time.LocalDateTime.now());

        attemptRepository.save(attempt);

        // Borrar key de Redis
        redisTemplate.delete(REDIS_KEY_PREFIX + attemptId);

        // Calificación automática (puede pasar a GRADED si no hay preguntas manuales)
        gradingService.autoGrade(attemptId);

        // Recarga el intento actualizado
        attempt = attemptRepository.findById(attemptId).orElse(attempt);
        return toAttemptResponse(attempt, null);
    }

    /** Lista de exámenes publicados accesibles al estudiante: disponibles ahora + programados (B7.6) */
    public List<ExamSummaryResponse> getAvailableExams(UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        Long academyId = TenantContext.getCurrentAcademyId();
        LocalTime now = LocalTime.now();

        List<Exam> published = examRepository.findAllPublishedForStudent(academyId, now);
        if (published.isEmpty()) return List.of();

        // Carga matrículas del estudiante en 2 queries (en lugar de N*M*K queries dentro del loop)
        Set<Long> enrolledCourseIds = enrollmentRepository.findEnrolledCourseIds(student.getId());
        Set<Long> enrolledAreaIds   = enrollmentRepository.findEnrolledAreaIds(student.getId());

        // Batch load: courseId → areaId para exámenes de scope COURSE
        Set<Long> courseScopeIds = published.stream()
                .filter(e -> "COURSE".equals(e.getScopeType()) && e.getCourseId() != null)
                .map(Exam::getCourseId).collect(Collectors.toSet());
        Map<Long, Long> courseToAreaId = courseScopeIds.isEmpty() ? Map.of() :
                courseRepository.findAllById(courseScopeIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getAreaId));

        // Batch load: areaId → Set<courseId> para exámenes de scope AREA
        Set<Long> areaScopeIds = published.stream()
                .filter(e -> "AREA".equals(e.getScopeType()) && e.getAreaId() != null)
                .map(Exam::getAreaId).collect(Collectors.toSet());
        Map<Long, Set<Long>> areaToCourseIds = Map.of();
        if (!areaScopeIds.isEmpty()) {
            areaToCourseIds = courseRepository
                    .findByAreaIdInAndAcademyIdAndIsActiveTrue(new ArrayList<>(areaScopeIds), academyId)
                    .stream().collect(Collectors.groupingBy(Course::getAreaId,
                            Collectors.mapping(Course::getId, Collectors.toSet())));
        }

        // Batch load: cycleId → Set<areaId> + areaId → Set<courseId> para scope CYCLE
        Set<Long> cycleScopeIds = published.stream()
                .filter(e -> "CYCLE".equals(e.getScopeType()) && e.getCycleId() != null)
                .map(Exam::getCycleId).collect(Collectors.toSet());
        Map<Long, Set<Long>> cycleToAreaIds     = Map.of();
        Map<Long, Set<Long>> cycleAreaToCourseIds = Map.of();
        if (!cycleScopeIds.isEmpty()) {
            List<Area> cycleAreas = areaRepository.findByCycleIdInAndAcademyId(cycleScopeIds, academyId);
            cycleToAreaIds = cycleAreas.stream().collect(Collectors.groupingBy(Area::getCycleId,
                    Collectors.mapping(Area::getId, Collectors.toSet())));
            Set<Long> allCycleAreaIds = cycleAreas.stream().map(Area::getId).collect(Collectors.toSet());
            if (!allCycleAreaIds.isEmpty()) {
                cycleAreaToCourseIds = courseRepository
                        .findByAreaIdInAndAcademyIdAndIsActiveTrue(new ArrayList<>(allCycleAreaIds), academyId)
                        .stream().collect(Collectors.groupingBy(Course::getAreaId,
                                Collectors.mapping(Course::getId, Collectors.toSet())));
            }
        }

        final Map<Long, Long>      finalCourseToAreaId       = courseToAreaId;
        final Map<Long, Set<Long>> finalAreaToCourseIds      = areaToCourseIds;
        final Map<Long, Set<Long>> finalCycleToAreaIds       = cycleToAreaIds;
        final Map<Long, Set<Long>> finalCycleAreaToCourseIds = cycleAreaToCourseIds;

        return published.stream()
                .filter(exam -> hasAccessFromContext(exam, enrolledCourseIds, enrolledAreaIds,
                        finalCourseToAreaId, finalAreaToCourseIds,
                        finalCycleToAreaIds, finalCycleAreaToCourseIds))
                .filter(exam -> !"SIMULACRO".equals(exam.getScopeType()))
                .map(examService::toSummary)
                .toList();
    }

    /** Verifica acceso en memoria usando contexto preloaded — sin queries adicionales */
    private boolean hasAccessFromContext(Exam exam,
                                         Set<Long> enrolledCourseIds, Set<Long> enrolledAreaIds,
                                         Map<Long, Long> courseToAreaId,
                                         Map<Long, Set<Long>> areaToCourseIds,
                                         Map<Long, Set<Long>> cycleToAreaIds,
                                         Map<Long, Set<Long>> cycleAreaToCourseIds) {
        return switch (exam.getScopeType()) {
            case "COURSE" -> {
                if (enrolledCourseIds.contains(exam.getCourseId())) yield true;
                Long areaId = courseToAreaId.get(exam.getCourseId());
                yield areaId != null && enrolledAreaIds.contains(areaId);
            }
            case "AREA" -> {
                if (enrolledAreaIds.contains(exam.getAreaId())) yield true;
                yield areaToCourseIds.getOrDefault(exam.getAreaId(), Set.of())
                        .stream().anyMatch(enrolledCourseIds::contains);
            }
            case "CYCLE" -> {
                for (Long areaId : cycleToAreaIds.getOrDefault(exam.getCycleId(), Set.of())) {
                    if (enrolledAreaIds.contains(areaId)) yield true;
                    if (cycleAreaToCourseIds.getOrDefault(areaId, Set.of())
                            .stream().anyMatch(enrolledCourseIds::contains)) yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    /** B7.11 — Resultados del estudiante (lista) */
    public List<StudentResultResponse> getMyResults(UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        List<ExamAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        if (attempts.isEmpty()) return List.of();

        List<Long> examIds = attempts.stream().map(ExamAttempt::getExamId).distinct().toList();
        Map<Long, Exam> examMap = examRepository.findAllById(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));
        Map<Long, BigDecimal> maxScoreMap = examRepository.sumQuestionPointsByExamIdIn(examIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> (BigDecimal) row[1]
                ));

        return attempts.stream()
                .map(a -> toResultResponse(a, examMap.get(a.getExamId()), maxScoreMap.get(a.getExamId())))
                .toList();
    }

    /** B7.11 — Resultado detallado de un intento */
    public StudentResultResponse getResultDetail(Long attemptId, UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        ExamAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));
        Exam exam = examService.findOrFail(attempt.getExamId());
        return toResultResponse(attempt, exam);
    }

    /** Anti-cheat — registra una salida de pantalla durante el examen */
    @Transactional
    public void recordFocusLost(Long attemptId, UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        ExamAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));
        if (!"IN_PROGRESS".equals(attempt.getStatus())) return;
        attemptRepository.incrementFocusLostCount(attemptId);
    }

    // --- Calificación manual (admin/teacher) B7.10 ---

    /** Lista de intentos SUBMITTED pendientes de calificación manual, enriquecidos con datos del estudiante */
    public List<AttemptResponse> getPendingAttempts(Long examId) {
        List<ExamAttempt> attempts = attemptRepository.findByExamIdAndStatus(examId, STATUS_SUBMITTED);
        // Carga todos los estudiantes en una sola consulta
        List<Long> studentIds = attempts.stream().map(ExamAttempt::getStudentId).distinct().toList();
        Map<Long, Student> studentMap = studentRepository.findAllById(studentIds)
                .stream().collect(java.util.stream.Collectors.toMap(Student::getId, s -> s));
        return attempts.stream().map(a -> {
            Student s = studentMap.get(a.getStudentId());
            String fullName = s != null ? s.getFullName() : null;
            String dni      = s != null ? s.getDni()      : null;
            return toAttemptResponseWithStudent(a, fullName, dni);
        }).toList();
    }

    /** URL de descarga firmada para que admin/docente vea el archivo subido por el estudiante */
    public DownloadUrlResponse generateFileAnswerDownloadUrl(Long attemptId, String key) {
        attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));
        return new DownloadUrlResponse(storageService.generateDownloadUrl(key),
                storageService.getExpiryMinutes() * 60);
    }

    /** B7.9 — URL firmada para que el estudiante suba un archivo como respuesta */
    public UploadUrlResponse generateAttemptUploadUrl(Long attemptId, Long questionId,
                                                      String filename, String contentType,
                                                      UserPrincipal principal) {
        Student student = studentPortalService.resolveStudent(principal);
        ExamAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, student.getId())
                .orElseThrow(() -> new NotFoundException(ATTEMPT_NOT_FOUND + attemptId));

        if (!"IN_PROGRESS".equals(attempt.getStatus())) {
            throw new IllegalArgumentException("Solo puedes subir archivos en un intento en curso");
        }

        String safeContentType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        // Preserve file extension from original filename for readability
        String ext = (filename != null && filename.contains(".")) ? filename.substring(filename.lastIndexOf('.')) : "";
        String key = String.format("academy/%d/attempts/%d/q%d/%s%s",
                TenantContext.getCurrentAcademyId(), attemptId, questionId, UUID.randomUUID(), ext);
        return storageService.generateUploadUrl(key, safeContentType);
    }

    // --- Helpers ---

    /** B7.5 — Valida que el horario actual está dentro de la ventana del examen */
    private void validateSchedule(Exam exam) {
        LocalTime now = LocalTime.now();
        if (exam.getAvailableFrom() != null && now.isBefore(exam.getAvailableFrom())) {
            throw new AccessDeniedException("El examen no está disponible aún. Disponible desde: " + exam.getAvailableFrom());
        }
        if (exam.getAvailableUntil() != null && now.isAfter(exam.getAvailableUntil())) {
            throw new AccessDeniedException("El horario del examen ya cerró. Estuvo disponible hasta: " + exam.getAvailableUntil());
        }
    }

    /** Verifica acceso del estudiante según el scope del examen */
    private void assertStudentExamAccess(Long studentId, Exam exam) {
        if (!hasStudentAccessToExam(studentId, exam)) {
            throw new AccessDeniedException("No tienes acceso a este examen");
        }
    }

    private boolean hasStudentAccessToExam(Long studentId, Exam exam) {
        Long academyId = exam.getAcademyId();
        return switch (exam.getScopeType()) {
            case "COURSE" -> {
                if (enrollmentRepository.existsActiveCourseEnrollment(studentId, exam.getCourseId())) yield true;
                yield courseRepository.findById(exam.getCourseId())
                        .map(c -> enrollmentRepository.existsActiveAreaEnrollment(studentId, c.getAreaId()))
                        .orElse(false);
            }
            case "AREA" -> {
                if (enrollmentRepository.existsActiveAreaEnrollment(studentId, exam.getAreaId())) yield true;
                Set<Long> enrolledCourseIds = enrollmentRepository.findEnrolledCourseIds(studentId);
                yield courseRepository.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(exam.getAreaId(), academyId)
                        .stream().anyMatch(c -> enrolledCourseIds.contains(c.getId()));
            }
            case "CYCLE" -> {
                Set<Long> enrolledCourseIds = enrollmentRepository.findEnrolledCourseIds(studentId);
                Set<Long> enrolledAreaIds   = enrollmentRepository.findEnrolledAreaIds(studentId);
                List<Area> cycleAreas = areaRepository.findByCycleIdAndAcademyIdOrderByName(exam.getCycleId(), academyId);
                boolean areaMatch = cycleAreas.stream().anyMatch(a -> enrolledAreaIds.contains(a.getId()));
                if (areaMatch) yield true;
                Set<Long> cycleAreaIds = cycleAreas.stream().map(Area::getId).collect(Collectors.toSet());
                yield courseRepository.findByAreaIdInAndAcademyIdAndIsActiveTrue(new ArrayList<>(cycleAreaIds), academyId)
                        .stream().anyMatch(c -> enrolledCourseIds.contains(c.getId()));
            }
            case "SIMULACRO" -> {
                // Any active student in the academy can access — no enrollment check needed
                yield studentRepository.findById(studentId)
                        .map(Student::getIsActive)
                        .orElse(false);
            }
            default -> false;
        };
    }

    /**
     * Resolves remaining seconds for an in-progress attempt. The Redis TTL is the
     * primary source; if the key is missing (Redis restart/flush returns -2) or
     * Redis is unavailable, falls back to startedAt + exam duration so an attempt
     * is never auto-submitted prematurely just because Redis lost the key.
     */
    private long resolveRemainingSeconds(ExamAttempt attempt) {
        String key = REDIS_KEY_PREFIX + attempt.getId();
        Long ttl = null;
        try {
            ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while reading attempt timer {}: {}", attempt.getId(), e.getMessage());
        }
        if (ttl != null && ttl >= 0) {
            return ttl;
        }

        // Fallback: recompute from the persisted start time (authoritative source)
        Exam exam = examService.findOrFail(attempt.getExamId());
        long elapsed = Duration.between(attempt.getStartedAt(), java.time.LocalDateTime.now()).getSeconds();
        long remaining = (long) exam.getDurationMinutes() * 60 - elapsed;
        if (remaining > 0) {
            // Restore the Redis timer so subsequent polls are cheap again
            try {
                redisTemplate.opsForValue().set(key, "active", Duration.ofSeconds(remaining));
            } catch (DataAccessException e) {
                log.warn("Could not restore attempt timer {} in Redis: {}", attempt.getId(), e.getMessage());
            }
        }
        return Math.max(remaining, 0);
    }

    private void autoSubmitExpired(ExamAttempt attempt) {
        if (attempt.getAnswers() == null) {
            attempt.setAnswers(Map.of("answers", Map.of()));
        }
        attempt.setStatus(STATUS_SUBMITTED);
        attempt.setSubmittedAt(java.time.LocalDateTime.now());
        attemptRepository.save(attempt);
        gradingService.autoGrade(attempt.getId());
    }

    private AttemptResponse toAttemptResponse(ExamAttempt a, Long remainingSeconds) {
        return new AttemptResponse(a.getId(), a.getUuid(), a.getExamId(), a.getStudentId(),
                a.getAttemptNumber(), a.getStatus(), a.getStartedAt(), a.getSubmittedAt(),
                a.getAutoScore(), a.getManualScore(), a.getTotalScore(), a.getPassed(),
                remainingSeconds, a.getFocusLostCount(), a.getAnswers(), null, null);
    }

    private AttemptResponse toAttemptResponseWithStudent(ExamAttempt a, String studentFullName, String studentDni) {
        return new AttemptResponse(a.getId(), a.getUuid(), a.getExamId(), a.getStudentId(),
                a.getAttemptNumber(), a.getStatus(), a.getStartedAt(), a.getSubmittedAt(),
                a.getAutoScore(), a.getManualScore(), a.getTotalScore(), a.getPassed(),
                null, a.getFocusLostCount(), a.getAnswers(), studentFullName, studentDni);
    }

    private StudentResultResponse toResultResponse(ExamAttempt a, Exam exam) {
        return toResultResponse(a, exam, null);
    }

    @SuppressWarnings("unchecked")
    private StudentResultResponse toResultResponse(ExamAttempt a, Exam exam, BigDecimal maxScore) {
        Map<String, Object> answers = "GRADED".equals(a.getStatus()) ? a.getAnswers() : null;
        if (maxScore == null) {
            maxScore = examRepository.sumQuestionPoints(a.getExamId());
        }

        List<StudentResultResponse.QuestionFeedback> feedbackList = null;
        if ("GRADED".equals(a.getStatus()) && answers != null) {
            Map<String, Object> manualGrades   = (Map<String, Object>) answers.getOrDefault("manualGrades",   new HashMap<>());
            Map<String, Object> manualFeedback = (Map<String, Object>) answers.getOrDefault("manualFeedback", new HashMap<>());
            if (!manualGrades.isEmpty()) {
                List<Question> manualQs = questionRepository.findByExamIdOrderByOrderIndex(a.getExamId())
                        .stream().filter(Question::getRequiresManualGrading).toList();
                feedbackList = manualQs.stream().map(q -> {
                    Object rawScore = manualGrades.get(q.getId().toString());
                    BigDecimal score = rawScore != null ? new java.math.BigDecimal(rawScore.toString()) : null;
                    String fb = (String) manualFeedback.get(q.getId().toString());
                    return new StudentResultResponse.QuestionFeedback(q.getId(), q.getQuestionText(), score, q.getPoints(), fb);
                }).toList();
            }
        }

        return new StudentResultResponse(a.getId(), a.getUuid(), a.getExamId(), exam.getTitle(),
                a.getAttemptNumber(), a.getStatus(), a.getStartedAt(), a.getSubmittedAt(),
                a.getTotalScore(), maxScore, exam.getPassingScore(), a.getPassed(), answers, feedbackList);
    }
}
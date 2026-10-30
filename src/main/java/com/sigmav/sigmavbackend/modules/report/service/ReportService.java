package com.sigmav.sigmavbackend.modules.report.service;

import com.sigmav.sigmavbackend.modules.area.entity.Area;
import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.entity.CourseMaterial;
import com.sigmav.sigmavbackend.modules.course.repository.CourseMaterialRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.enrollment.entity.Enrollment;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.report.dto.*;
import com.sigmav.sigmavbackend.modules.session.entity.AttendanceRecord;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.session.repository.ClassSessionRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.entity.StudentProgress;
import com.sigmav.sigmavbackend.modules.student.repository.StudentProgressRepository;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ClassSessionRepository sessionRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final StudentRepository studentRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final CourseRepository courseRepo;
    private final AreaRepository areaRepo;
    private final CourseMaterialRepository materialRepo;
    private final StudentProgressRepository progressRepo;
    private final ExamAttemptRepository attemptRepo;
    private final ExamRepository examRepo;
    private final CycleRepository cycleRepo;
    private final UserRepository userRepo;

    // Constantes para SonarQube
    private static final String STATUS_PRESENT = "PRESENT";
    private static final String STATUS_LATE = "LATE";
    private static final String STATUS_ABSENT = "ABSENT";
    private static final String ENROLLMENT_TYPE_COURSE = "COURSE";
    private static final String ENROLLMENT_TYPE_AREA = "AREA";
    private static final String MSG_COURSE_NOT_FOUND = "Curso no encontrado: ";

    // ── B8.1 ─ Asistencia por sesión ─────────────────────────────────────────

    public AttendanceSessionReportResponse getAttendanceBySession(Long sessionId) {
        Long academyId = TenantContext.getCurrentAcademyId();

        ClassSession session = sessionRepo.findById(sessionId)
                .filter(s -> academyId.equals(s.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Sesión no encontrada: " + sessionId));

        Course course = courseRepo.findById(session.getCourseId())
                .filter(c -> academyId.equals(c.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Curso no encontrado"));

        List<Long> enrolledIds = enrollmentRepo.findEnrolledStudentIds(
                session.getCourseId(), course.getAreaId());

        Map<Long, AttendanceRecord> recordMap = attendanceRepo
                .findBySessionId(sessionId)
                .stream()
                .collect(Collectors.toMap(AttendanceRecord::getStudentId, r -> r));

        Map<Long, Student> studentMap = studentRepo.findAllById(enrolledIds).stream()
                .filter(st -> academyId.equals(st.getAcademyId()))
                .collect(Collectors.toMap(Student::getId, s -> s));

        List<AttendanceSessionReportResponse.Item> items = enrolledIds.stream()
                .map(sid -> {
                    Student student = studentMap.get(sid);
                    String name = student != null ? student.getFullName() : "Estudiante #" + sid;
                    AttendanceRecord rec = recordMap.get(sid);
                    if (rec != null) {
                        return new AttendanceSessionReportResponse.Item(
                                sid, name, rec.getStatus(), rec.getCheckInAt(), rec.getCheckOutAt());
                    }
                    return new AttendanceSessionReportResponse.Item(sid, name, STATUS_ABSENT, null, null);
                })
                .sorted(Comparator.comparing(AttendanceSessionReportResponse.Item::studentFullName))
                .toList();

        long present = items.stream().filter(i -> STATUS_PRESENT.equals(i.status())).count();
        long late    = items.stream().filter(i -> STATUS_LATE.equals(i.status())).count();
        long absent  = items.stream().filter(i -> STATUS_ABSENT.equals(i.status())).count();

        return new AttendanceSessionReportResponse(
                sessionId, session.getTitle(), session.getSessionDate(),
                items.size(), present, late, absent, items);
    }

    // ── B8.2 ─ Asistencia por estudiante ─────────────────────────────────────

    public AttendanceStudentReportResponse getAttendanceByStudent(Long studentId, Long courseId,
                                                                  LocalDate from, LocalDate to) {
        Long academyId = TenantContext.getCurrentAcademyId();

        Student student = studentRepo.findById(studentId)
                .filter(s -> academyId.equals(s.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado: " + studentId));
        Course course = courseRepo.findById(courseId)
                .filter(c -> academyId.equals(c.getAcademyId()))
                .orElseThrow(() -> new NotFoundException(MSG_COURSE_NOT_FOUND + courseId));

        List<ClassSession> sessions;
        if (from != null && to != null) {
            sessions = sessionRepo.findByCourseIdAndSessionDateBetweenOrderBySessionDate(courseId, from, to);
        } else {
            sessions = sessionRepo.findByCourseIdOrderBySessionDateDesc(courseId);
        }

        List<Long> sessionIds = sessions.stream().map(ClassSession::getId).toList();
        Map<Long, AttendanceRecord> recordBySession = attendanceRepo.findBySessionIdIn(sessionIds)
                .stream()
                .filter(r -> r.getStudentId().equals(studentId))
                .collect(Collectors.toMap(AttendanceRecord::getSessionId, r -> r));

        List<AttendanceStudentReportResponse.Item> items = sessions.stream()
                .map(s -> {
                    AttendanceRecord rec = recordBySession.get(s.getId());
                    String status  = rec != null ? rec.getStatus() : STATUS_ABSENT;
                    LocalDateTime checkIn = rec != null ? rec.getCheckInAt() : null;
                    return new AttendanceStudentReportResponse.Item(
                            s.getId(), s.getTitle(), s.getSessionDate(), status, checkIn);
                })
                .toList();

        long present = items.stream().filter(i -> STATUS_PRESENT.equals(i.status())).count();
        long late    = items.stream().filter(i -> STATUS_LATE.equals(i.status())).count();
        long absent  = items.stream().filter(i -> STATUS_ABSENT.equals(i.status())).count();

        return new AttendanceStudentReportResponse(
                studentId, student.getFullName(), courseId, course.getName(),
                sessions.size(), present, late, absent, items);
    }

    // ── B8.3 ─ Matrículas — cantidad (Refactorizado para bajar Complejidad Cognitiva) ──

    public EnrollmentCountReportResponse getEnrollmentCount(Long cycleId, Long areaId, Long courseId) {
        List<EnrollmentCountReportResponse.Item> breakdown = new ArrayList<>();
        long total;

        if (courseId != null) {
            total = processCourseCount(courseId, breakdown);
        } else if (areaId != null) {
            total = processAreaCount(areaId, breakdown);
        } else if (cycleId != null) {
            total = processCycleCount(cycleId, breakdown);
        } else {
            total = enrollmentRepo.countByAcademyIdAndIsActiveTrue(TenantContext.getCurrentAcademyId());
        }

        return new EnrollmentCountReportResponse(total, breakdown);
    }

    private long processCourseCount(Long courseId, List<EnrollmentCountReportResponse.Item> breakdown) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Course course = courseRepo.findById(courseId)
                .filter(c -> academyId.equals(c.getAcademyId()))
                .orElseThrow(() -> new NotFoundException(MSG_COURSE_NOT_FOUND + courseId));
        long count = enrollmentRepo.countByIsActiveTrueAndCourseIdAndEnrollmentType(courseId, ENROLLMENT_TYPE_COURSE);
        breakdown.add(new EnrollmentCountReportResponse.Item(courseId, course.getName(), ENROLLMENT_TYPE_COURSE, count));
        return count;
    }

    private long processAreaCount(Long areaId, List<EnrollmentCountReportResponse.Item> breakdown) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Area area = areaRepo.findById(areaId)
                .filter(a -> academyId.equals(a.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Área no encontrada: " + areaId));
        return calculateAreaAndCourses(area, breakdown);
    }

    private long processCycleCount(Long cycleId, List<EnrollmentCountReportResponse.Item> breakdown) {
        long total = 0;
        Long academyId = TenantContext.getCurrentAcademyId();
        for (Area area : areaRepo.findByCycleIdAndAcademyIdAndIsActiveTrueOrderByName(cycleId, academyId)) {
            total += calculateAreaAndCourses(area, breakdown);
        }
        return total;
    }

    private long calculateAreaAndCourses(Area area, List<EnrollmentCountReportResponse.Item> breakdown) {
        long total = 0;
        Long academyId = TenantContext.getCurrentAcademyId();
        long areaCount = enrollmentRepo.countByIsActiveTrueAndAreaIdAndEnrollmentType(area.getId(), ENROLLMENT_TYPE_AREA);
        breakdown.add(new EnrollmentCountReportResponse.Item(area.getId(), area.getName(), ENROLLMENT_TYPE_AREA, areaCount));
        total += areaCount;

        for (Course c : courseRepo.findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(area.getId(), academyId)) {
            long cc = enrollmentRepo.countByIsActiveTrueAndCourseIdAndEnrollmentType(c.getId(), ENROLLMENT_TYPE_COURSE);
            breakdown.add(new EnrollmentCountReportResponse.Item(c.getId(), c.getName(), ENROLLMENT_TYPE_COURSE, cc));
            total += cc;
        }
        return total;
    }

    // ── B8.4 ─ Matrículas — financiero ───────────────────────────────────────

    public EnrollmentRevenueReportResponse getEnrollmentRevenue(LocalDate from, LocalDate to) {
        Long academyId = TenantContext.getCurrentAcademyId();
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59) : null;

        List<Enrollment> enrollments = enrollmentRepo.findForRevenue(academyId, fromDt, toDt);

        Map<YearMonth, BigDecimal> monthRevenue = new TreeMap<>();
        Map<YearMonth, Long>       monthCount   = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Enrollment e : enrollments) {
            YearMonth ym = YearMonth.from(e.getEnrolledAt());
            monthRevenue.merge(ym, e.getAmountPaid(), BigDecimal::add);
            monthCount.merge(ym, 1L, Long::sum);
            total = total.add(e.getAmountPaid());
        }

        List<EnrollmentRevenueReportResponse.PeriodItem> breakdown = monthRevenue.entrySet().stream()
                .map(entry -> new EnrollmentRevenueReportResponse.PeriodItem(
                        entry.getKey().toString(),
                        entry.getValue(),
                        monthCount.get(entry.getKey())))
                .toList();

        return new EnrollmentRevenueReportResponse(total, "PEN", breakdown);
    }

    // ── B8.x ─ Matrículas — detalle ──────────────────────────────────────────

    public EnrollmentDetailReportResponse getEnrollmentDetail(
            Long cycleId, Long areaId, Long courseId, LocalDate from, LocalDate to) {

        Long academyId = TenantContext.getCurrentAcademyId();
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59) : null;

        List<Enrollment> enrollments = fetchEnrollmentsForDetail(
                academyId, cycleId, areaId, courseId, fromDt, toDt);

        if (enrollments.isEmpty()) {
            return new EnrollmentDetailReportResponse(0, List.of());
        }

        Map<Long, String> areaNames   = resolveAreaNames(enrollments);
        Map<Long, String> courseNames = resolveCourseNames(enrollments);
        Map<Long, Student> students   = resolveStudents(enrollments);

        LocalDateTime now = LocalDateTime.now();
        List<EnrollmentDetailReportResponse.Item> items = new ArrayList<>();
        for (int i = 0; i < enrollments.size(); i++) {
            items.add(toDetailItem(i + 1, enrollments.get(i), students, areaNames, courseNames, now));
        }

        return new EnrollmentDetailReportResponse(items.size(), items);
    }

    private List<Enrollment> fetchEnrollmentsForDetail(Long academyId, Long cycleId, Long areaId,
                                                       Long courseId, LocalDateTime from, LocalDateTime to) {
        // Si se especifica área o curso, usar filtro directo (más específico que el ciclo)
        if (cycleId == null || areaId != null || courseId != null) {
            // courseId y areaId son mutuamente excluyentes (distinto enrollmentType)
            Long effectiveAreaId = courseId != null ? null : areaId;
            return enrollmentRepo.findForDetail(academyId, courseId, effectiveAreaId, from, to);
        }
        // Solo cycleId sin área ni curso → traer todo el ciclo
        List<Long> areaIds = areaRepo
                .findByCycleIdAndAcademyIdAndIsActiveTrueOrderByName(cycleId, academyId)
                .stream().map(a -> a.getId()).toList();
        if (areaIds.isEmpty()) return List.of();

        List<Long> courseIds = courseRepo
                .findByAreaIdInAndAcademyIdAndIsActiveTrue(areaIds, academyId)
                .stream().map(c -> c.getId()).toList();

        return enrollmentRepo.findForDetailByCycleScope(academyId, areaIds,
                courseIds.isEmpty() ? List.of(-1L) : courseIds, from, to);
    }

    private Map<Long, String> resolveAreaNames(List<Enrollment> enrollments) {
        List<Long> ids = enrollments.stream()
                .filter(e -> ENROLLMENT_TYPE_AREA.equals(e.getEnrollmentType()))
                .map(Enrollment::getAreaId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return areaRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getName()));
    }

    private Map<Long, String> resolveCourseNames(List<Enrollment> enrollments) {
        List<Long> ids = enrollments.stream()
                .filter(e -> ENROLLMENT_TYPE_COURSE.equals(e.getEnrollmentType()))
                .map(Enrollment::getCourseId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return courseRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
    }

    private Map<Long, Student> resolveStudents(List<Enrollment> enrollments) {
        List<Long> ids = enrollments.stream().map(Enrollment::getStudentId).distinct().toList();
        return studentRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
    }

    private EnrollmentDetailReportResponse.Item toDetailItem(
            int numero, Enrollment e, Map<Long, Student> students,
            Map<Long, String> areaNames, Map<Long, String> courseNames, LocalDateTime now) {

        Student student = students.get(e.getStudentId());
        String studentName = student != null ? student.getFullName() : "Estudiante #" + e.getStudentId();
        String dni         = student != null ? student.getDni()      : "-";

        String programa = ENROLLMENT_TYPE_AREA.equals(e.getEnrollmentType())
                ? areaNames.getOrDefault(e.getAreaId(), "Área #" + e.getAreaId())
                : courseNames.getOrDefault(e.getCourseId(), "Curso #" + e.getCourseId());

        String status;
        if (!Boolean.TRUE.equals(e.getIsActive())) {
            status = "INACTIVA";
        } else if (e.getExpiresAt() != null && e.getExpiresAt().isBefore(now)) {
            status = "VENCIDA";
        } else {
            status = "ACTIVA";
        }

        return new EnrollmentDetailReportResponse.Item(
                numero, e.getId(), dni, studentName, programa, e.getEnrollmentType(),
                e.getEnrolledAt(), e.getExpiresAt(), e.getAmountPaid(), e.getCurrency(),
                e.getPaymentMethod() != null ? e.getPaymentMethod().name() : null,
                status);
    }

    // ── B8.5 ─ Rendimiento en exámenes ───────────────────────────────────────

    // ── B8.5 ─ Rendimiento en exámenes (Refactorizado) ───────────────────────

    public ExamPerformanceReportResponse getExamPerformance(Long examId) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Exam exam = examRepo.findById(examId)
                .filter(e -> academyId.equals(e.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Examen no encontrado: " + examId));

        List<ExamAttempt> attempts = attemptRepo.findByExamId(examId)
                .stream()
                .filter(a -> "GRADED".equals(a.getStatus()))
                .toList();

        if (attempts.isEmpty()) {
            return createEmptyPerformanceResponse(examId, exam.getTitle());
        }

        return buildPerformanceResponse(exam, attempts);
    }

    private ExamPerformanceReportResponse createEmptyPerformanceResponse(Long examId, String title) {
        return new ExamPerformanceReportResponse(
                examId, title, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, 0.0, List.of(), List.of());
    }

    private ExamPerformanceReportResponse buildPerformanceResponse(Exam exam, List<ExamAttempt> attempts) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal max = null;
        BigDecimal min = null;
        int passedCount = 0;

        // Calcular puntaje máximo posible del examen
        BigDecimal maxPossible = examRepo.sumQuestionPoints(exam.getId());
        if (maxPossible == null || maxPossible.compareTo(BigDecimal.ZERO) == 0) {
            maxPossible = BigDecimal.valueOf(100);
        }

        double bucketSize = maxPossible.doubleValue() / 5.0;
        int[] buckets = new int[5];

        for (ExamAttempt a : attempts) {
            BigDecimal score = a.getTotalScore() != null ? a.getTotalScore() : BigDecimal.ZERO;
            sum = sum.add(score);
            if (max == null || score.compareTo(max) > 0) max = score;
            if (min == null || score.compareTo(min) < 0) min = score;
            if (Boolean.TRUE.equals(a.getPassed())) passedCount++;

            int bucket = Math.min((int) (score.doubleValue() / bucketSize), 4);
            buckets[bucket]++;
        }

        BigDecimal avg = sum.divide(new BigDecimal(attempts.size()), 2, RoundingMode.HALF_UP);
        int failedCount = attempts.size() - passedCount;
        double passRate = Math.round((passedCount * 100.0 / attempts.size()) * 10.0) / 10.0;

        return new ExamPerformanceReportResponse(
                exam.getId(), exam.getTitle(), attempts.size(),
                avg,
                max != null ? max : BigDecimal.ZERO,
                min != null ? min : BigDecimal.ZERO,
                passedCount, failedCount, passRate,
                buildDistribution(buckets, maxPossible.doubleValue()),
                getTop10Students(attempts));
    }

    private List<ExamPerformanceReportResponse.ScoreRange> buildDistribution(int[] buckets, double maxPossible) {
        double bucketSize = maxPossible / 5.0;
        List<ExamPerformanceReportResponse.ScoreRange> distribution = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String label = String.format("%.0f-%.0f", i * bucketSize, (i + 1) * bucketSize);
            distribution.add(new ExamPerformanceReportResponse.ScoreRange(label, buckets[i]));
        }
        return distribution;
    }

    private List<ExamPerformanceReportResponse.TopStudent> getTop10Students(List<ExamAttempt> attempts) {
        Long academyId = TenantContext.getCurrentAcademyId();
        List<ExamAttempt> top10 = attempts.stream()
                .sorted(Comparator.comparing(
                        a -> a.getTotalScore() != null ? a.getTotalScore() : BigDecimal.ZERO,
                        Comparator.reverseOrder()))
                .limit(10)
                .toList();

        List<Long> studentIds = top10.stream().map(ExamAttempt::getStudentId).distinct().toList();
        Map<Long, Student> studentMap = studentRepo.findAllById(studentIds).stream()
                .filter(st -> academyId.equals(st.getAcademyId()))
                .collect(Collectors.toMap(Student::getId, s -> s));

        return top10.stream()
                .map(a -> {
                    Student s = studentMap.get(a.getStudentId());
                    String name = s != null ? s.getFullName() : "Estudiante #" + a.getStudentId();
                    return new ExamPerformanceReportResponse.TopStudent(
                            a.getStudentId(), name,
                            a.getTotalScore() != null ? a.getTotalScore() : BigDecimal.ZERO,
                            Boolean.TRUE.equals(a.getPassed()),
                            a.getFocusLostCount() != null ? a.getFocusLostCount() : 0);
                })
                .toList();
    }

    // ── B8.x ─ Notas de examen (Excel) ───────────────────────────────────────

    public ExamGradesReportResponse getExamGrades(Long examId) {
        Long academyId = TenantContext.getCurrentAcademyId();

        Exam exam = examRepo.findById(examId)
                .filter(e -> academyId.equals(e.getAcademyId()))
                .orElseThrow(() -> new NotFoundException("Examen no encontrado: " + examId));

        String scopeName  = resolveScopeName(exam, academyId);
        String teacherName = userRepo.findById(exam.getCreatedBy())
                .map(u -> u.getFullName())
                .orElse("—");

        List<ExamAttempt> attempts = attemptRepo.findByExamId(examId).stream()
                .filter(a -> !"IN_PROGRESS".equals(a.getStatus()))
                .sorted(Comparator.comparing(ExamAttempt::getStartedAt))
                .toList();

        List<Long> studentIds = attempts.stream().map(ExamAttempt::getStudentId).distinct().toList();
        Map<Long, Student> studentMap = studentRepo.findAllById(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        int passedCount = 0;
        int failedCount = 0;
        List<ExamGradesReportResponse.AttemptItem> grades = new ArrayList<>();

        for (int i = 0; i < attempts.size(); i++) {
            ExamAttempt a = attempts.get(i);
            Student student = studentMap.get(a.getStudentId());
            String name  = student != null ? student.getFullName() : "Estudiante #" + a.getStudentId();
            String dni   = student != null ? student.getDni()      : "—";

            String result = resolveResult(a, exam.getPassingScore());
            if ("APROBADO".equals(result))   passedCount++;
            if ("DESAPROBADO".equals(result)) failedCount++;

            grades.add(new ExamGradesReportResponse.AttemptItem(
                    i + 1, a.getStudentId(), dni, name,
                    a.getTotalScore(), result,
                    a.getFocusLostCount() != null ? a.getFocusLostCount() : 0));
        }

        return new ExamGradesReportResponse(
                examId, exam.getTitle(), exam.getScopeType(), scopeName, teacherName,
                exam.getPassingScore(), attempts.size(), passedCount, failedCount, grades);
    }

    private String resolveScopeName(Exam exam, Long academyId) {
        return switch (exam.getScopeType()) {
            case "COURSE" -> courseRepo.findByIdAndAcademyId(exam.getCourseId(), academyId)
                    .map(Course::getName).orElse("Curso #" + exam.getCourseId());
            case "AREA"   -> areaRepo.findById(exam.getAreaId())
                    .filter(a -> academyId.equals(a.getAcademyId()))
                    .map(Area::getName).orElse("Área #" + exam.getAreaId());
            case "CYCLE"  -> cycleRepo.findByIdAndAcademyId(exam.getCycleId(), academyId)
                    .map(c -> c.getName()).orElse("Ciclo #" + exam.getCycleId());
            default -> "—";
        };
    }

    private String resolveResult(ExamAttempt a, BigDecimal passingScore) {
        if ("GRADED".equals(a.getStatus())) {
            return Boolean.TRUE.equals(a.getPassed()) ? "APROBADO" : "DESAPROBADO";
        }
        if ("SUBMITTED".equals(a.getStatus())) return "PENDIENTE";
        return "EN_CURSO";
    }

    // ── Libro de calificaciones ───────────────────────────────────────────────

    public GradebookResponse getGradebook(Long courseId) {
        Long academyId = TenantContext.getCurrentAcademyId();

        Course course = courseRepo.findByIdAndAcademyId(courseId, academyId)
                .orElseThrow(() -> new NotFoundException(MSG_COURSE_NOT_FOUND + courseId));

        Area area = areaRepo.findById(course.getAreaId())
                .filter(a -> academyId.equals(a.getAcademyId()))
                .orElse(null);

        // Exámenes de scope COURSE para este curso
        List<Exam> exams = examRepo.findByCourseIdAndAcademyIdOrderByCreatedAtAsc(courseId, academyId);

        // Estudiantes matriculados (por curso directo o por área)
        List<Long> studentIds = enrollmentRepo.findEnrolledStudentIds(courseId, course.getAreaId());
        List<Student> students = studentRepo.findAllById(studentIds).stream()
                .filter(s -> academyId.equals(s.getAcademyId()))
                .sorted(Comparator.comparing(Student::getFullName))
                .toList();

        // Mejor intento por (examen, estudiante)
        Map<Long, Map<Long, ExamAttempt>> bestByExamByStudent = new HashMap<>();
        Map<Long, BigDecimal> maxScoreByExam = new HashMap<>();

        if (!exams.isEmpty()) {
            List<Long> examIds = exams.stream().map(Exam::getId).toList();

            Map<Long, List<ExamAttempt>> attemptsByExam = attemptRepo.findByExamIdIn(examIds).stream()
                    .collect(Collectors.groupingBy(ExamAttempt::getExamId));

            Map<Long, BigDecimal> rawMaxScores = examRepo.sumQuestionPointsByExamIdIn(examIds).stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row[0]).longValue(),
                            row -> (BigDecimal) row[1]
                    ));

            for (Exam exam : exams) {
                List<ExamAttempt> attempts = attemptsByExam.getOrDefault(exam.getId(), List.of());
                Map<Long, ExamAttempt> bestPerStudent = attempts.stream()
                        .filter(a -> !"IN_PROGRESS".equals(a.getStatus()))
                        .collect(Collectors.toMap(
                                ExamAttempt::getStudentId,
                                a -> a,
                                (a1, a2) -> {
                                    BigDecimal s1 = a1.getTotalScore() != null ? a1.getTotalScore() : BigDecimal.ZERO;
                                    BigDecimal s2 = a2.getTotalScore() != null ? a2.getTotalScore() : BigDecimal.ZERO;
                                    return s1.compareTo(s2) >= 0 ? a1 : a2;
                                }
                        ));
                bestByExamByStudent.put(exam.getId(), bestPerStudent);
                BigDecimal maxScore = rawMaxScores.get(exam.getId());
                maxScoreByExam.put(exam.getId(), maxScore != null ? maxScore : BigDecimal.ZERO);
            }
        }

        List<GradebookResponse.GradebookExam> gradebookExams = exams.stream()
                .map(e -> new GradebookResponse.GradebookExam(
                        e.getId(), e.getTitle(), e.getPassingScore(),
                        maxScoreByExam.getOrDefault(e.getId(), BigDecimal.ZERO)))
                .toList();

        List<GradebookResponse.GradebookStudent> gradebookStudents = students.stream()
                .map(s -> {
                    List<GradebookResponse.GradebookScore> scores = exams.stream()
                            .map(e -> {
                                ExamAttempt attempt = bestByExamByStudent
                                        .getOrDefault(e.getId(), Map.of()).get(s.getId());
                                return new GradebookResponse.GradebookScore(
                                        e.getId(),
                                        attempt != null ? attempt.getTotalScore() : null,
                                        attempt != null ? attempt.getPassed() : null,
                                        attempt != null ? attempt.getStatus() : null);
                            })
                            .toList();
                    return new GradebookResponse.GradebookStudent(
                            s.getId(), s.getFullName(), s.getDni(), scores);
                })
                .toList();

        return new GradebookResponse(
                courseId, course.getName(),
                area != null ? area.getId() : null,
                area != null ? area.getName() : null,
                gradebookExams, gradebookStudents);
    }

    // ── B8.6 ─ Progreso de materiales ────────────────────────────────────────

    public MaterialProgressReportResponse getMaterialProgress(Long courseId) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Course course = courseRepo.findById(courseId)
                .filter(c -> academyId.equals(c.getAcademyId()))
                .orElseThrow(() -> new NotFoundException(MSG_COURSE_NOT_FOUND + courseId));

        List<CourseMaterial> materials = materialRepo
                .findByCourseIdAndIsActiveTrueOrderByOrderIndex(courseId);
        List<Long> materialIds = materials.stream().map(CourseMaterial::getId).toList();

        List<Long> enrolledIds = enrollmentRepo.findEnrolledStudentIds(courseId, course.getAreaId());
        int totalStudents = enrolledIds.size();

        Set<Long> enrolledSet = new HashSet<>(enrolledIds);
        Map<Long, Long> viewsByMaterial = progressRepo.findByMaterialIdIn(materialIds)
                .stream()
                .filter(p -> enrolledSet.contains(p.getStudentId()))
                .collect(Collectors.groupingBy(StudentProgress::getMaterialId, Collectors.counting()));

        List<MaterialProgressReportResponse.Item> items = materials.stream()
                .map(m -> {
                    long viewed = viewsByMaterial.getOrDefault(m.getId(), 0L);
                    double rate = totalStudents > 0
                            ? Math.round((viewed * 100.0 / totalStudents) * 10.0) / 10.0
                            : 0.0;
                    return new MaterialProgressReportResponse.Item(
                            m.getId(), m.getTitle(), m.getMaterialType(), (int) viewed, rate);
                })
                .toList();

        return new MaterialProgressReportResponse(courseId, course.getName(), totalStudents, items);
    }
}
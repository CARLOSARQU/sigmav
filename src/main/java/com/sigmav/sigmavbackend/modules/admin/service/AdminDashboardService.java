package com.sigmav.sigmavbackend.modules.admin.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.admin.dto.AcademyLimitsResponse;
import com.sigmav.sigmavbackend.modules.admin.dto.AdminDashboardResponse;
import com.sigmav.sigmavbackend.modules.admin.dto.BrandingUpdateRequest;
import com.sigmav.sigmavbackend.modules.admin.dto.ChangePasswordRequest;
import com.sigmav.sigmavbackend.modules.course.entity.Course;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.session.entity.ClassSession;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.session.repository.ClassSessionRepository;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * B8.7 — Dashboard del Admin de academia.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final StudentRepository studentRepo;
    private final CourseRepository courseRepo;
    private final EnrollmentRepository enrollmentRepo;
    private final ExamRepository examRepo;
    private final AttendanceRecordRepository attendanceRepo;
    private final AcademyRepository academyRepo;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClassSessionRepository sessionRepo;
    private final CacheManager cacheManager;

    @Cacheable(value = "dashboard", key = "T(com.sigmav.sigmavbackend.security.TenantContext).getCurrentAcademyId()")
    public AdminDashboardResponse getDashboard() {
        Long academyId = TenantContext.getCurrentAcademyId();

        long activeStudents = studentRepo.countByAcademyIdAndIsActiveTrue(academyId);
        long activeCourses  = courseRepo.countByAcademyIdAndIsActiveTrue(academyId);

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now          = LocalDateTime.now();

        long enrollmentsThisMonth   = enrollmentRepo.countByAcademyIdAndEnrolledAtBetween(academyId, startOfMonth, now);
        BigDecimal revenueThisMonth = enrollmentRepo.sumAmountPaidInPeriodByAcademy(academyId, startOfMonth, now);

        long upcomingExams = examRepo.countByAcademyIdAndStatus(academyId, "PUBLISHED");

        long totalRecords    = attendanceRepo.countByAcademyId(academyId);
        long attendedRecords = attendanceRepo.countAttendedByAcademy(academyId);
        double attendanceRateAvg = totalRecords > 0
                ? Math.round((attendedRecords * 100.0 / totalRecords) * 10.0) / 10.0
                : 0.0;

        // --- Financial breakdown ---
        // Last month revenue
        LocalDateTime startOfLastMonth = now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfLastMonth = startOfMonth;
        BigDecimal revenueLastMonth = enrollmentRepo.sumAmountPaidInPeriodByAcademy(academyId, startOfLastMonth, endOfLastMonth);
        if (revenueLastMonth == null) revenueLastMonth = BigDecimal.ZERO;

        // Payment status breakdown
        Map<String, Long> statusCounts = new HashMap<>();
        enrollmentRepo.countByPaymentStatusGrouped(academyId)
            .forEach(row -> statusCounts.put(String.valueOf(row[0]), ((Number) row[1]).longValue()));

        // Monthly revenue series — always return exactly 6 months (fill missing with zero)
        LocalDateTime sixMonthsAgo = now.minusMonths(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        Map<String, Object[]> dataByMonth = enrollmentRepo.monthlyRevenueSeries(academyId, sixMonthsAgo)
            .stream()
            .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> row));

        List<AdminDashboardResponse.MonthlyRevenue> monthlyRevenueSeries = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth ym = java.time.YearMonth.now().minusMonths(i);
            String key = ym.toString();
            Object[] row = dataByMonth.get(key);
            monthlyRevenueSeries.add(row != null
                ? new AdminDashboardResponse.MonthlyRevenue(key, new BigDecimal(row[1].toString()), ((Number) row[2]).longValue())
                : new AdminDashboardResponse.MonthlyRevenue(key, BigDecimal.ZERO, 0L)
            );
        }

        // Pending collections — reserved for future use; status counts drive the UI
        BigDecimal pendingCollections = BigDecimal.ZERO;

        return new AdminDashboardResponse(
                activeStudents,
                activeCourses,
                enrollmentsThisMonth,
                revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO,
                upcomingExams,
                attendanceRateAvg,
                revenueLastMonth,
                pendingCollections,
                statusCounts.getOrDefault("PAGADO", 0L),
                statusCounts.getOrDefault("PARCIAL", 0L),
                statusCounts.getOrDefault("PENDIENTE", 0L),
                statusCounts.getOrDefault("VENCIDO", 0L),
                monthlyRevenueSeries);
    }

    /** POST /api/admin/profile/change-password — cambia la contraseña del admin autenticado */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        cacheManager.getCache("user-principals").evict(userId);
    }

    /** PUT /api/admin/branding — actualiza logo, color primario y features de login */
    @Transactional
    public void updateBranding(Long academyId, BrandingUpdateRequest request) {
        Academy academy = academyRepo.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada"));
        if (request.logoUrl() != null) {
            academy.getConfig().put("logoUrl", request.logoUrl());
        }
        if (request.primaryColor() != null) {
            academy.getConfig().put("primaryColor", request.primaryColor());
        }
        if (request.features() != null) {
            academy.getConfig().put("loginFeatures", request.features());
        }
        academyRepo.save(academy);
    }

    /** PUT /api/admin/report-schedule — configura día y hora de reportes para apoderados */
    @Transactional
    @CacheEvict(value = "academy-limits", key = "#academyId")
    public void updateReportSchedule(Long academyId, String reportScheduleDay, Integer reportScheduleHour) {
        Academy academy = academyRepo.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada"));
        academy.getConfig().put("reportScheduleDay", reportScheduleDay);
        academy.getConfig().put("reportScheduleHour", reportScheduleHour);
        academyRepo.save(academy);
    }

    /** GET /api/admin/calendar — sesiones activas de la academia en un rango de fechas */
    public List<Map<String, Object>> getCalendarEvents(LocalDate from, LocalDate to) {
        Long academyId = TenantContext.getCurrentAcademyId();

        List<ClassSession> sessions = sessionRepo
                .findByAcademyIdAndIsActiveTrueAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                        academyId, from, to);

        // Batch-load course names
        List<Long> courseIds = sessions.stream().map(ClassSession::getCourseId).distinct().toList();
        Map<Long, String> courseNames = courseRepo.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));

        return sessions.stream().map(s -> Map.<String, Object>of(
                "id", s.getId(),
                "type", "SESSION",
                "date", s.getSessionDate().toString(),
                "startTime", s.getStartTime().toString(),
                "endTime", s.getEndTime().toString(),
                "title", s.getTitle(),
                "courseId", s.getCourseId(),
                "courseName", courseNames.getOrDefault(s.getCourseId(), "Curso #" + s.getCourseId())
        )).toList();
    }

    /** GET /api/admin/my-academy — límites del plan y uso actual para el frontend */
    @Cacheable(value = "academy-limits", key = "T(com.sigmav.sigmavbackend.security.TenantContext).getCurrentAcademyId()")
    public AcademyLimitsResponse getAcademyLimits() {
        Long academyId = TenantContext.getCurrentAcademyId();

        Academy academy = academyRepo.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada"));

        long activeCourses  = courseRepo.countByAcademyIdAndIsActiveTrue(academyId);
        long activeStudents = studentRepo.countByAcademyIdAndIsActiveTrue(academyId);

        return new AcademyLimitsResponse(
                academy.getMaxCourses(),
                academy.getMaxStudents(),
                activeCourses,
                activeStudents,
                getConfigString(academy, "reportScheduleDay", "MONDAY"),
                getConfigInt(academy, "reportScheduleHour", 8)
        );
    }

    private String getConfigString(Academy academy, String key, String defaultValue) {
        if (academy.getConfig() == null) return defaultValue;
        Object val = academy.getConfig().get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int getConfigInt(Academy academy, String key, int defaultValue) {
        if (academy.getConfig() == null) return defaultValue;
        Object val = academy.getConfig().get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { /* fall through */ }
        }
        return defaultValue;
    }
}

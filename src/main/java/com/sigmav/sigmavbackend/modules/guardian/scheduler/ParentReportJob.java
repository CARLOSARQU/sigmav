package com.sigmav.sigmavbackend.modules.guardian.scheduler;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.auth.service.EmailService;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.guardian.entity.Guardian;
import com.sigmav.sigmavbackend.modules.guardian.repository.GuardianRepository;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParentReportJob {

    private final AcademyRepository academyRepository;
    private final GuardianRepository guardianRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamRepository examRepository;
    private final EmailService emailService;

    /**
     * Revisa cada hora si alguna academia tiene configurado el envío de reportes
     * para el día/hora actual. Cada academia define su propio día y hora.
     */
    @Scheduled(cron = "0 0 * * * *")  // cada hora en punto
    @Transactional
    public void sendWeeklyReports() {
        LocalDateTime now = LocalDateTime.now();
        String today = now.getDayOfWeek().toString(); // MONDAY, TUESDAY, ...
        int currentHour = now.getHour();

        List<Academy> academies = academyRepository.findByIsActiveTrue();
        log.info("ParentReportJob: checking {} active academies (today={}, hour={})",
                academies.size(), today, currentHour);

        for (Academy academy : academies) {
            // Leer día/hora configurados para esta academia (default: LUNES 8 AM)
            String scheduledDay = getConfig(academy, "reportScheduleDay", "MONDAY");
            int scheduledHour = Integer.parseInt(getConfig(academy, "reportScheduleHour", "8"));

            if (!today.equalsIgnoreCase(scheduledDay) || currentHour != scheduledHour) {
                continue; // no es el momento configurado para esta academia
            }

            TenantContext.setCurrentAcademyId(academy.getId());
            try {
                long guardianCount = guardianRepository.countByAcademyId(academy.getId());
                if (guardianCount == 0) {
                    log.info("ParentReportJob: academy '{}' (id={}): skipped (no guardian data)",
                            academy.getName(), academy.getId());
                    continue;
                }

                List<Guardian> guardians = guardianRepository
                        .findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(academy.getId())
                        .stream()
                        .filter(g -> !Boolean.FALSE.equals(g.getSendReports()))
                        .toList();

                int sent = 0;
                int skipped = 0;

                for (Guardian guardian : guardians) {
                    try {
                        Optional<Student> studentOpt = studentRepository
                                .findByIdAndAcademyId(guardian.getStudentId(), academy.getId());
                        if (studentOpt.isEmpty() || !Boolean.TRUE.equals(studentOpt.get().getIsActive())) {
                            skipped++;
                            continue;
                        }

                        Student student = studentOpt.get();

                        // Estado de matrícula
                        boolean hasActiveEnrollment = enrollmentRepository
                                .existsActiveEnrollmentForStudent(student.getId());

                        // Asistencia de la última semana
                        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
                        long totalSessions = attendanceRecordRepository
                                .countByStudentIdAndCheckInAtAfter(student.getId(), weekAgo);
                        long attendedSessions = attendanceRecordRepository
                                .countAttendedByStudentIdSince(student.getId(), weekAgo);

                        // Últimos 3 intentos de examen
                        List<ExamAttempt> recentAttempts = examAttemptRepository
                                .findByStudentIdOrderByStartedAtDesc(student.getId());
                        List<ExamAttempt> latest3 = recentAttempts.size() > 3
                                ? recentAttempts.subList(0, 3)
                                : recentAttempts;

                        String subject = "Reporte Semanal — " + student.getFullName();
                        String html = buildReportHtml(academy, student, hasActiveEnrollment,
                                totalSessions, attendedSessions, latest3);

                        emailService.sendHtmlEmail(guardian.getEmail(), subject, html);
                        log.info("ParentReportJob: sent report for guardian {} (student: {})",
                                guardian.getId(), student.getId());
                        sent++;
                    } catch (Exception e) {
                        log.error("ParentReportJob: error processing guardian {} for student {}: {}",
                                guardian.getId(), guardian.getStudentId(), e.getMessage(), e);
                        skipped++;
                    }
                }

                log.info("ParentReportJob: academy '{}': {} sent, {} skipped",
                        academy.getName(), sent, skipped);
            } finally {
                TenantContext.clear();
            }
        }

        log.info("ParentReportJob: finished processing all academies");
    }

    private String buildReportHtml(Academy academy, Student student, boolean hasActiveEnrollment,
                                   long totalSessions, long attendedSessions,
                                   List<ExamAttempt> recentAttempts) {
        String enrollmentBadge = hasActiveEnrollment
                ? "<span style=\"background:#22c55e;color:#fff;padding:4px 12px;border-radius:6px;font-size:13px;font-weight:600\">ACTIVO</span>"
                : "<span style=\"background:#ef4444;color:#fff;padding:4px 12px;border-radius:6px;font-size:13px;font-weight:600\">VENCIDO</span>";

        String attendanceText;
        if (totalSessions > 0) {
            long pct = (attendedSessions * 100) / totalSessions;
            attendanceText = String.format("%d de %d sesiones asistidas (%d%%)", attendedSessions, totalSessions, pct);
        } else {
            attendanceText = "No hay sesiones registradas esta semana";
        }

        String examRows = "";
        if (recentAttempts.isEmpty()) {
            examRows = "<tr><td colspan=\"3\" style=\"padding:12px;text-align:center;color:#999\">Sin exámenes rendidos</td></tr>";
        } else {
            examRows = recentAttempts.stream()
                    .map(this::formatExamRow)
                    .collect(Collectors.joining("\n"));
        }

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"/></head>
            <body style="margin:0;padding:0;background:#f4f4f5;font-family:Inter,Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="max-width:560px;margin:40px auto;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.08)">
                <tr>
                  <td style="background:#6366f1;padding:32px 40px;text-align:center">
                    <h1 style="color:#fff;margin:0;font-size:22px;font-weight:700">%s</h1>
                  </td>
                </tr>
                <tr>
                  <td style="padding:40px">
                    <h2 style="color:#111;font-size:18px;margin:0 0 8px">Reporte Semanal</h2>
                    <p style="color:#666;margin:0 0 24px;font-size:14px">
                      Semana del %s
                    </p>

                    <div style="background:#f9fafb;border-radius:8px;padding:16px;margin-bottom:24px">
                      <p style="margin:0 0 4px;font-size:15px;font-weight:600;color:#111">
                        %s
                      </p>
                      <p style="margin:0;font-size:13px;color:#666">
                        Código: %s
                      </p>
                    </div>

                    <div style="margin-bottom:24px">
                      <h3 style="font-size:14px;color:#555;margin:0 0 8px;text-transform:uppercase;letter-spacing:0.5px">
                        Estado de Matrícula
                      </h3>
                      %s
                    </div>

                    <div style="margin-bottom:24px">
                      <h3 style="font-size:14px;color:#555;margin:0 0 8px;text-transform:uppercase;letter-spacing:0.5px">
                        Asistencia Semanal
                      </h3>
                      <p style="margin:0;font-size:15px;color:#111">%s</p>
                    </div>

                    <div style="margin-bottom:24px">
                      <h3 style="font-size:14px;color:#555;margin:0 0 8px;text-transform:uppercase;letter-spacing:0.5px">
                        Últimos Exámenes
                      </h3>
                      <table width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                        <thead>
                          <tr style="background:#f1f5f9">
                            <th style="padding:10px 12px;text-align:left;font-size:12px;color:#555;text-transform:uppercase;letter-spacing:0.5px">Examen</th>
                            <th style="padding:10px 12px;text-align:center;font-size:12px;color:#555;text-transform:uppercase;letter-spacing:0.5px">Nota</th>
                            <th style="padding:10px 12px;text-align:center;font-size:12px;color:#555;text-transform:uppercase;letter-spacing:0.5px">Resultado</th>
                          </tr>
                        </thead>
                        <tbody>
                          %s
                        </tbody>
                      </table>
                    </div>

                    <p style="color:#999;font-size:12px;margin:32px 0 0;text-align:center">
                      Este reporte es generado automáticamente por %s.<br/>
                      Si tienes dudas, contacta a la academia directamente.
                    </p>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                academy.getName(),
                LocalDate.now().minusDays(7).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " — " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                student.getFullName(),
                student.getCode() != null ? student.getCode() : "—",
                enrollmentBadge,
                attendanceText,
                examRows,
                academy.getName()
        );
    }

    private String formatExamRow(ExamAttempt attempt) {
        String examName = resolveExamName(attempt.getExamId());
        String score = attempt.getTotalScore() != null
                ? String.valueOf(attempt.getTotalScore())
                : "—";
        String passed = attempt.getPassed() != null
                ? (attempt.getPassed()
                    ? "<span style=\"color:#22c55e;font-weight:600\">Aprobado</span>"
                    : "<span style=\"color:#ef4444;font-weight:600\">Desaprobado</span>")
                : "<span style=\"color:#999\">Pendiente</span>";

        return String.format("""
                <tr style="border-bottom:1px solid #f1f5f9">
                  <td style="padding:10px 12px;font-size:14px;color:#333">%s</td>
                  <td style="padding:10px 12px;text-align:center;font-size:14px;color:#333">%s</td>
                  <td style="padding:10px 12px;text-align:center;font-size:14px">%s</td>
                </tr>
                """, examName, score, passed);
    }

    private String resolveExamName(Long examId) {
        if (examId == null) return "—";
        try {
            return examRepository.findById(examId)
                    .map(Exam::getTitle)
                    .orElse("Examen #" + examId);
        } catch (Exception e) {
            log.warn("ParentReportJob: could not resolve exam name for id={}", examId);
            return "Examen #" + examId;
        }
    }

    private String getConfig(Academy academy, String key, String defaultValue) {
        if (academy.getConfig() == null) return defaultValue;
        Object val = academy.getConfig().get(key);
        return val != null ? val.toString() : defaultValue;
    }
}

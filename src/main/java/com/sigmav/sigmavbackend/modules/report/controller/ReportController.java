package com.sigmav.sigmavbackend.modules.report.controller;

import com.sigmav.sigmavbackend.modules.report.dto.*;
import com.sigmav.sigmavbackend.modules.report.service.ExcelExportService;
import com.sigmav.sigmavbackend.modules.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ExcelExportService excelService;

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String LABEL_MATRICULAS = "Matrículas";

    // ── B8.1 ─ Asistencia por sesión ─────────────────────────────────────────

    @GetMapping("/attendance/session/{sessionId}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<Object> attendanceBySession(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "false") boolean excel) {

        AttendanceSessionReportResponse report = reportService.getAttendanceBySession(sessionId);

        if (excel) {
            List<String> headers = List.of("Estudiante", "Estado", "Hora Entrada", "Hora Salida");
            List<List<String>> rows = report.attendance().stream()
                    .map(item -> List.of(
                            item.studentFullName(),
                            item.status(),
                            item.checkInAt() != null ? item.checkInAt().toString() : "",
                            item.checkOutAt() != null ? item.checkOutAt().toString() : ""))
                    .toList();
            byte[] bytes = excelService.generateExcel("Asistencia", headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"asistencia-sesion-" + sessionId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .body(bytes);
        }

        return ResponseEntity.ok(report);
    }

    // ── B8.2 ─ Asistencia por estudiante ─────────────────────────────────────

    @GetMapping("/attendance/student/{studentId}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<AttendanceStudentReportResponse> attendanceByStudent(
            @PathVariable Long studentId,
            @RequestParam Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(
                reportService.getAttendanceByStudent(studentId, courseId, from, to));
    }

    // ── B8.3 ─ Matrículas — cantidad ─────────────────────────────────────────

    @GetMapping("/enrollments/count")
    @PreAuthorize("hasAuthority('PERMISSION_VIEW_ENROLLMENTS')")
    public ResponseEntity<Object> enrollmentCount(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "false") boolean excel) {

        EnrollmentCountReportResponse report =
                reportService.getEnrollmentCount(cycleId, areaId, courseId);

        if (excel) {
            List<String> headers = List.of("ID", "Nombre", "Tipo", LABEL_MATRICULAS);
            List<List<String>> rows = report.breakdown().stream()
                    .map(item -> List.of(
                            String.valueOf(item.id()),
                            item.name(),
                            item.type(),
                            String.valueOf(item.count())))
                    .toList();
            byte[] bytes = excelService.generateExcel(LABEL_MATRICULAS, headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"matriculas-cantidad.xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .body(bytes);
        }

        return ResponseEntity.ok(report);
    }

    // ── B8.4 ─ Matrículas — financiero ───────────────────────────────────────

    @GetMapping("/enrollments/revenue")
    @PreAuthorize("hasAuthority('PERMISSION_READ_FINANCES')")
    public ResponseEntity<Object> enrollmentRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean excel) {

        EnrollmentRevenueReportResponse report = reportService.getEnrollmentRevenue(from, to);

        if (excel) {
            List<String> headers = List.of("Período", "Ingresos", LABEL_MATRICULAS);
            List<List<String>> rows = report.breakdown().stream()
                    .map(item -> List.of(
                            item.period(),
                            item.revenue().toPlainString(),
                            String.valueOf(item.enrollmentCount())))
                    .toList();
            byte[] bytes = excelService.generateExcel("Ingresos", headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"matriculas-ingresos.xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .body(bytes);
        }

        return ResponseEntity.ok(report);
    }

    // ── B8.x ─ Matrículas — detalle ──────────────────────────────────────────

    @GetMapping("/enrollments/detail")
    @PreAuthorize("hasAuthority('PERMISSION_READ_FINANCES')")
    public ResponseEntity<Object> enrollmentDetail(
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean excel) {

        EnrollmentDetailReportResponse report =
                reportService.getEnrollmentDetail(cycleId, areaId, courseId, from, to);

        if (excel) {
            List<String> headers = List.of(
                    "N°", "ID", "DNI", "Estudiante", "Programa/Nivel", "Tipo",
                    "Fecha Matrícula", "Fecha Vencimiento", "Monto", "Moneda",
                    "Método de Pago", "Estado");
            List<List<String>> rows = report.items().stream()
                    .map(item -> List.of(
                            String.valueOf(item.numero()),
                            String.valueOf(item.enrollmentId()),
                            item.dni(),
                            item.studentName(),
                            item.programa(),
                            item.enrollmentType(),
                            item.enrolledAt() != null ? item.enrolledAt().toLocalDate().toString() : "",
                            item.expiresAt()  != null ? item.expiresAt().toLocalDate().toString()  : "",
                            item.amountPaid() != null ? item.amountPaid().toPlainString() : "",
                            item.currency()   != null ? item.currency() : "",
                            item.paymentMethod() != null ? item.paymentMethod() : "",
                            item.status()))
                    .toList();
            byte[] bytes = excelService.generateExcel(LABEL_MATRICULAS, headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"matriculas-detalle.xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .body(bytes);
        }

        return ResponseEntity.ok(report);
    }

    // ── B8.5 ─ Rendimiento en exámenes ───────────────────────────────────────

    @GetMapping("/exams/{examId}/performance")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<ExamPerformanceReportResponse> examPerformance(
            @PathVariable Long examId) {

        return ResponseEntity.ok(reportService.getExamPerformance(examId));
    }

    // ── B8.x ─ Notas de examen (lista completa + Excel) ──────────────────────

    @GetMapping("/exams/{examId}/grades")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<Object> examGrades(
            @PathVariable Long examId,
            @RequestParam(defaultValue = "false") boolean excel) {

        ExamGradesReportResponse report = reportService.getExamGrades(examId);

        if (excel) {
            List<String> headers = List.of(
                    "N°", "DNI", "Estudiante", "Nota", "Nota Aprobatoria", "Resultado", "Salidas de Pantalla");
            List<List<String>> rows = report.grades().stream()
                    .map(item -> List.of(
                            String.valueOf(item.numero()),
                            item.dni(),
                            item.studentName(),
                            item.score() != null ? item.score().toPlainString() : "",
                            report.passingScore().toPlainString(),
                            item.result(),
                            String.valueOf(item.focusLostCount())))
                    .toList();
            String sheetName = report.examTitle().length() > 30
                    ? report.examTitle().substring(0, 30) : report.examTitle();
            byte[] bytes = excelService.generateExcel(sheetName, headers, rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"notas-examen-" + examId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(EXCEL_CONTENT_TYPE))
                    .body(bytes);
        }

        return ResponseEntity.ok(report);
    }

    // ── B8.6 ─ Progreso de materiales ────────────────────────────────────────

    @GetMapping("/courses/{courseId}/progress")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<MaterialProgressReportResponse> materialProgress(
            @PathVariable Long courseId) {

        return ResponseEntity.ok(reportService.getMaterialProgress(courseId));
    }

    // ── Libro de calificaciones ───────────────────────────────────────────────

    @GetMapping("/courses/{courseId}/gradebook")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<GradebookResponse> gradebook(@PathVariable Long courseId) {
        return ResponseEntity.ok(reportService.getGradebook(courseId));
    }
}
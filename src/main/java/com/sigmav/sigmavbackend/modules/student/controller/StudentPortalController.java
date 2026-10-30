package com.sigmav.sigmavbackend.modules.student.controller;

import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.dto.StudentMaterialResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentSessionResponse;
import com.sigmav.sigmavbackend.modules.course.service.CourseMaterialService;
import com.sigmav.sigmavbackend.modules.exam.dto.*;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.storage.dto.UploadUrlResponse;
import com.sigmav.sigmavbackend.modules.exam.service.ExamAttemptService;
import com.sigmav.sigmavbackend.modules.session.dto.CheckInRequest;
import com.sigmav.sigmavbackend.modules.session.service.AttendanceService;
import com.sigmav.sigmavbackend.modules.student.dto.StudentCalendarResponse;
import com.sigmav.sigmavbackend.modules.student.dto.StudentCourseResponse;
import com.sigmav.sigmavbackend.modules.student.service.StudentPortalService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Portal del estudiante — endpoints exclusivos para el rol STUDENT.
 * B5.6, B5.7, B5.8, B6.4, B6.5, B6.6
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentPortalController {

    private final StudentPortalService portalService;
    private final AttendanceService    attendanceService;
    private final ExamAttemptService   examAttemptService;
    private final CourseMaterialService materialService;
    private final StorageService       storageService;

    /** Mis datos de estudiante — para mostrar perfil y generar carnet */
    @GetMapping("/me")
    public ResponseEntity<StudentResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getMyProfile(principal));
    }

    /** Subir mi propia foto de perfil */
    @PostMapping("/me/avatar")
    public ResponseEntity<StudentResponse> uploadMyAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.uploadMyAvatar(file, principal, storageService));
    }

    /** B5.6 — Mis cursos con jerarquía ciclo → área → curso */
    @GetMapping("/courses")
    public ResponseEntity<List<StudentCourseResponse>> getMyCourses(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getMyCourses(principal));
    }

    /** B5.7 — Materiales de un curso (valida acceso + registra progreso) */
    @GetMapping("/courses/{courseId}/materials")
    public ResponseEntity<List<StudentMaterialResponse>> getMaterials(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getMaterials(courseId, principal));
    }

    /** B5.8 — Actualizar perfil: solo password (el email lo administra el admin) */
    @PatchMapping("/profile")
    public ResponseEntity<Void> updateProfile(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        portalService.updateProfile(principal, body.get("password"));
        return ResponseEntity.noContent().build();
    }

    // --- Asistencia del estudiante ---

    /** Sesiones de los cursos matriculados (últimos 7 días + próximos 30) con estado de asistencia */
    @GetMapping("/sessions")
    public ResponseEntity<List<StudentSessionResponse>> getMySessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getMySessions(principal));
    }

    /** B6.4 — Marcar presencia en modo BUTTON */
    @PostMapping("/sessions/{sessionId}/attend")
    public ResponseEntity<Void> attend(@PathVariable Long sessionId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.markButtonAttendance(sessionId, principal);
        return ResponseEntity.noContent().build();
    }

    /** B6.5 — Check-in QR */
    @PostMapping("/sessions/{sessionId}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable Long sessionId,
                                        @Valid @RequestBody CheckInRequest request,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.checkIn(sessionId, request.qrToken(), principal);
        return ResponseEntity.noContent().build();
    }

    /** B6.6 — Check-out QR */
    @PostMapping("/sessions/{sessionId}/check-out")
    public ResponseEntity<Void> checkOut(@PathVariable Long sessionId,
                                         @Valid @RequestBody CheckInRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.checkOut(sessionId, request.qrToken(), principal);
        return ResponseEntity.noContent().build();
    }

    // --- Exámenes (B7.6 – B7.11) ---

    /** Preguntas de un intento en curso — sin revelar respuestas correctas */
    @GetMapping("/attempts/{attemptId}/questions")
    public ResponseEntity<List<QuestionResponse>> getAttemptQuestions(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getAttemptQuestions(attemptId, principal));
    }

    /** B7.6 — Exámenes disponibles ahora para el estudiante */
    @GetMapping("/exams")
    public ResponseEntity<List<ExamSummaryResponse>> getAvailableExams(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.getAvailableExams(principal));
    }

    /** B7.6 — Iniciar intento */
    @PostMapping("/exams/{examId}/start")
    public ResponseEntity<AttemptResponse> startAttempt(@PathVariable Long examId,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.startAttempt(examId, principal));
    }

    /** B7.7 — Tiempo restante del intento */
    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<AttemptResponse> getAttempt(@PathVariable Long attemptId,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.getAttempt(attemptId, principal));
    }

    /** B7.8 — Enviar respuestas */
    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<AttemptResponse> submit(@PathVariable Long attemptId,
                                                  @Valid @RequestBody SubmitAnswersRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.submit(attemptId, request, principal));
    }

    /** Anti-cheat — registra una salida de pantalla durante el examen */
    @PatchMapping("/attempts/{attemptId}/focus-event")
    public ResponseEntity<Void> recordFocusLost(@PathVariable Long attemptId,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        examAttemptService.recordFocusLost(attemptId, principal);
        return ResponseEntity.noContent().build();
    }

    /** B7.9 — URL firmada de R2 para subir archivo como respuesta de examen */
    @PostMapping("/attempts/{attemptId}/questions/{questionId}/upload-url")
    public ResponseEntity<Map<String, String>> getAttemptUploadUrl(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String filename = body.getOrDefault("filename", "file");
        String contentType = body.getOrDefault("contentType", "application/octet-stream");
        UploadUrlResponse res = examAttemptService.generateAttemptUploadUrl(attemptId, questionId, filename, contentType, principal);
        return ResponseEntity.ok(Map.of("uploadUrl", res.uploadUrl(), "fileUrl", res.key()));
    }

    /** B7.11 — Lista de resultados del estudiante */
    @GetMapping("/results")
    public ResponseEntity<List<StudentResultResponse>> getMyResults(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.getMyResults(principal));
    }

    /** B7.11 — Detalle de un resultado */
    @GetMapping("/results/{attemptId}")
    public ResponseEntity<StudentResultResponse> getResultDetail(@PathVariable Long attemptId,
                                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examAttemptService.getResultDetail(attemptId, principal));
    }

    /** Calendario — sesiones en rango + exámenes publicados activos */
    @GetMapping("/calendar")
    public ResponseEntity<StudentCalendarResponse> getCalendar(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.getCalendarData(from, to, principal));
    }

    /** Marca un material como visto al abrirlo */
    @PostMapping("/materials/{uuid}/viewed")
    public ResponseEntity<Void> markViewed(@PathVariable String uuid,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        portalService.markViewed(uuid, principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * URL de descarga privada para material PDF almacenado en Cloudflare R2.
     * Solo accesible si el estudiante tiene matrícula activa en el curso del material.
     * La URL expira en 15 minutos (configurable en application.yml).
     *
     * Flujo:
     *   1. Frontend recibe la lista de materiales (materialType = "PDF_S3")
     *   2. Para abrir el PDF: GET /api/student/materials/{uuid}/url
     *   3. Backend valida matrícula → retorna URL temporal de R2
     *   4. Frontend redirige a esa URL (o abre en nueva pestaña)
     */
    @GetMapping("/materials/{uuid}/url")
    public ResponseEntity<DownloadUrlResponse> getMaterialDownloadUrl(
            @PathVariable String uuid,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(portalService.generateMaterialDownloadUrl(uuid, principal));
    }
}

package com.sigmav.sigmavbackend.modules.exam.controller;

import com.sigmav.sigmavbackend.config.aop.RequiresFeature;
import com.sigmav.sigmavbackend.modules.academy.entity.Feature;
import com.sigmav.sigmavbackend.modules.exam.dto.*;
import com.sigmav.sigmavbackend.modules.storage.dto.DownloadUrlResponse;
import com.sigmav.sigmavbackend.modules.exam.service.ExamAttemptService;
import com.sigmav.sigmavbackend.modules.exam.service.ExamService;
import com.sigmav.sigmavbackend.modules.exam.service.GradingService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamAttemptService attemptService;
    private final GradingService gradingService;

    // --- CRUD Exámenes (B7.3) ---

    @GetMapping
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<List<ExamSummaryResponse>> listAll() {
        return ResponseEntity.ok(examService.listAll());
    }

    @GetMapping("/{id}")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<ExamResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getById(id));
    }

    @PostMapping
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<ExamResponse> create(@Valid @RequestBody CreateExamRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.create(request, principal));
    }

    @PatchMapping("/{id}")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<ExamResponse> update(@PathVariable Long id,
                                               @RequestBody UpdateExamRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examService.update(id, request, principal));
    }

    // --- Preguntas (B7.3) ---

    @PostMapping("/{examId}/questions")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<QuestionResponse> addQuestion(@PathVariable Long examId,
                                                        @Valid @RequestBody CreateQuestionRequest request,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examService.addQuestion(examId, request, principal));
    }

    @PatchMapping("/{examId}/questions/{questionId}")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long examId,
                                                           @PathVariable Long questionId,
                                                           @RequestBody UpdateQuestionRequest request,
                                                           @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examService.updateQuestion(examId, questionId, request, principal));
    }

    @DeleteMapping("/{examId}/questions/{questionId}")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long examId,
                                               @PathVariable Long questionId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        examService.deleteQuestion(examId, questionId, principal);
        return ResponseEntity.noContent().build();
    }

    // --- Flujo de estados (B7.4) ---

    @PostMapping("/{id}/publish")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<ExamSummaryResponse> publish(@PathVariable Long id,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examService.publish(id, principal));
    }

    @PostMapping("/{id}/finish")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<ExamSummaryResponse> finish(@PathVariable Long id,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(examService.finish(id, principal));
    }

    /**
     * Re-envía notificaciones in-app a los estudiantes matriculados.
     * Al publicar ya se notifica automáticamente; este endpoint permite re-enviar.
     */
    @PostMapping("/{id}/notify")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<Void> notifyStudents(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        examService.notifyStudents(id, principal);
        return ResponseEntity.noContent().build();
    }

    // --- Calificación manual (B7.10) ---

    @GetMapping("/{examId}/attempts/pending")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<List<AttemptResponse>> getPendingAttempts(@PathVariable Long examId) {
        return ResponseEntity.ok(attemptService.getPendingAttempts(examId));
    }

    @PatchMapping("/attempts/{attemptId}/grade-question")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<Void> gradeQuestion(@PathVariable Long attemptId,
                                              @Valid @RequestBody GradeQuestionRequest request,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        gradingService.gradeQuestion(attemptId, request.questionId(),
                request.score(), request.feedback(), principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** Califica todas las preguntas manuales de un intento en una sola operación atómica */
    @PatchMapping("/attempts/{attemptId}/grade-all")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<Void> gradeAll(@PathVariable Long attemptId,
                                         @Valid @RequestBody GradeAllRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        gradingService.gradeAllQuestions(attemptId, request, principal.getId());
        return ResponseEntity.noContent().build();
    }

    /** URL de descarga firmada para ver el archivo de respuesta de un estudiante (calificación manual) */
    @GetMapping("/attempts/{attemptId}/file-answer-url")
    @RequiresFeature(Feature.EXAMS)
    public ResponseEntity<DownloadUrlResponse> getFileAnswerUrl(@PathVariable Long attemptId,
                                                                @RequestParam String key) {
        return ResponseEntity.ok(attemptService.generateFileAnswerDownloadUrl(attemptId, key));
    }

    // --- PDF Export (B7.12) ---

    @GetMapping("/{id}/export/pdf")
    @RequiresFeature(Feature.PDF_EXPORT)
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) throws IOException {
        byte[] pdf = examService.exportToPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exam-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}

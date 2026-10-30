package com.sigmav.sigmavbackend.modules.exam.service;

import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.dto.*;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.Question;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.modules.notification.service.NotificationService;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {

    private final ExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final NotificationService notificationService;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;

    public List<ExamSummaryResponse> listAll() {
        return examRepository.findByAcademyIdOrderByCreatedAtDesc(TenantContext.getCurrentAcademyId())
                .stream().map(this::toSummary).toList();
    }

    public ExamResponse getById(Long id) {
        Exam exam = findOrFail(id);
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(id);
        return toResponse(exam, questions);
    }

    @Transactional
    public ExamResponse create(CreateExamRequest request, UserPrincipal principal) {
        validateScope(request.scopeType(), request.cycleId(), request.areaId(), request.courseId());

        Exam exam = Exam.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .scopeType(request.scopeType().toUpperCase())
                .cycleId(request.cycleId())
                .areaId(request.areaId())
                .courseId(request.courseId())
                .createdBy(principal.getId())
                .title(request.title())
                .description(request.description())
                .scheduledDate(request.scheduledDate())
                .availableFrom(request.availableFrom())
                .availableUntil(request.availableUntil())
                .durationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 60)
                .maxAttempts(request.maxAttempts() != null ? request.maxAttempts() : 1)
                .passingScore(request.passingScore() != null ? request.passingScore() : java.math.BigDecimal.valueOf(50))
                .build();

        return toResponse(examRepository.save(exam), List.of());
    }

    @Transactional
    public ExamResponse update(Long id, UpdateExamRequest request, UserPrincipal principal) {
        Exam exam = findOrFail(id);
        assertExamAccess(exam, principal);
        assertDraft(exam, "actualizar");

        if (request.title() != null) exam.setTitle(request.title());
        if (request.description() != null) exam.setDescription(request.description());
        if (request.scheduledDate() != null) exam.setScheduledDate(request.scheduledDate());
        if (request.availableFrom() != null) exam.setAvailableFrom(request.availableFrom());
        if (request.availableUntil() != null) exam.setAvailableUntil(request.availableUntil());
        if (request.durationMinutes() != null) exam.setDurationMinutes(request.durationMinutes());
        if (request.maxAttempts() != null) exam.setMaxAttempts(request.maxAttempts());
        if (request.passingScore() != null) exam.setPassingScore(request.passingScore());

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(id);
        return toResponse(examRepository.save(exam), questions);
    }

    // --- Gestión de preguntas (B7.3) ---

    @CacheEvict(value = "exam-max-score", key = "#examId")
    @Transactional
    public QuestionResponse addQuestion(Long examId, CreateQuestionRequest request, UserPrincipal principal) {
        Exam exam = findOrFail(examId);
        assertExamAccess(exam, principal);
        assertDraft(exam, "añadir preguntas a");

        String type = request.questionType().toUpperCase();
        validateQuestionType(type);

        Question question = Question.builder()
                .academyId(TenantContext.getCurrentAcademyId())
                .examId(examId)
                .questionText(request.questionText())
                .questionType(type)
                .options(request.options())
                .correctAnswer(request.correctAnswer())
                .points(request.points())
                .orderIndex(request.orderIndex() != null ? request.orderIndex() : 0)
                .requiresManualGrading("TEXT".equals(type) || "FILE_UPLOAD".equals(type))
                .build();

        return toQuestionResponse(questionRepository.save(question));
    }

    @CacheEvict(value = "exam-max-score", key = "#examId")
    @Transactional
    public QuestionResponse updateQuestion(Long examId, Long questionId,
                                           UpdateQuestionRequest request, UserPrincipal principal) {
        Exam exam = findOrFail(examId);
        assertExamAccess(exam, principal);
        assertDraft(exam, "editar preguntas de");

        Question question = questionRepository.findById(questionId)
                .filter(q -> q.getExamId().equals(examId))
                .orElseThrow(() -> new NotFoundException("Pregunta no encontrada con ID: " + questionId));

        if (request.questionText() != null) question.setQuestionText(request.questionText());
        if (request.options() != null) question.setOptions(request.options());
        if (request.correctAnswer() != null) question.setCorrectAnswer(request.correctAnswer());
        if (request.points() != null) question.setPoints(request.points());
        if (request.orderIndex() != null) question.setOrderIndex(request.orderIndex());

        return toQuestionResponse(questionRepository.save(question));
    }

    @CacheEvict(value = "exam-max-score", key = "#examId")
    @Transactional
    public void deleteQuestion(Long examId, Long questionId, UserPrincipal principal) {
        Exam exam = findOrFail(examId);
        assertExamAccess(exam, principal);
        assertDraft(exam, "eliminar preguntas de");

        Question question = questionRepository.findById(questionId)
                .filter(q -> q.getExamId().equals(examId))
                .orElseThrow(() -> new NotFoundException("Pregunta no encontrada con ID: " + questionId));
        questionRepository.delete(question);
    }

    // --- Flujo de estados (B7.4) ---

    @Transactional
    public ExamSummaryResponse publish(Long id, UserPrincipal principal) {
        Exam exam = findOrFail(id);
        assertExamAccess(exam, principal);

        if (!"DRAFT".equals(exam.getStatus())) {
            throw new IllegalArgumentException("Solo se puede publicar un examen en estado DRAFT");
        }
        if (questionRepository.countByExamId(id) == 0) {
            throw new IllegalArgumentException("El examen debe tener al menos una pregunta para publicarse");
        }

        exam.setStatus("PUBLISHED");
        exam.setIsPublished(true);
        Exam saved = examRepository.save(exam);

        // Notificar automáticamente a los estudiantes matriculados
        sendExamNotification(saved);

        return toSummary(saved);
    }

    /**
     * Re-envía la notificación de examen publicado a los estudiantes matriculados.
     * Útil cuando la notificación automática falló o se quiere recordar a los alumnos.
     */
    @Transactional
    public void notifyStudents(Long id, UserPrincipal principal) {
        Exam exam = findOrFail(id);
        assertExamAccess(exam, principal);
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new IllegalArgumentException("Solo se puede notificar sobre exámenes publicados.");
        }
        sendExamNotification(exam);
    }

    /**
     * Crea notificaciones in-app para los estudiantes afectados por el examen según su alcance:
     * - COURSE: estudiantes matriculados en el curso (directa o por área)
     * - AREA:   estudiantes matriculados en el área
     * - CYCLE:  todos los usuarios de la academia (notificación de academia)
     */
    private void sendExamNotification(Exam exam) {
        String title   = "Nuevo examen disponible";
        String message = "El examen «" + exam.getTitle() + "» ya está disponible. ¡Prepárate!";
        String type    = "EXAM_PUBLISHED";

        Long academyId = exam.getAcademyId();
        switch (exam.getScopeType()) {
            case "COURSE" -> {
                List<Long> studentIds = enrollmentRepository
                        .findEnrolledStudentIds(exam.getCourseId(), exam.getAreaId());
                notifyStudentList(studentIds, academyId, type, title, message);
            }
            case "AREA" -> {
                List<Long> studentIds = enrollmentRepository
                        .findEnrolledStudentIds(null, exam.getAreaId());
                notifyStudentList(studentIds, academyId, type, title, message);
            }
            case "CYCLE" -> {
                List<Long> studentIds = enrollmentRepository.findAllActiveStudentIds(academyId);
                notifyStudentList(studentIds, academyId, type, title, message);
            }
            case "SIMULACRO" -> {
                // Deferred — no automatic notification for simulacros in this phase.
                // Future: notify all active students of the academy.
                log.debug("SIMULACRO notification skipped for exam {} (not yet implemented)", exam.getId());
            }
        }
    }

    @Transactional
    public ExamSummaryResponse finish(Long id, UserPrincipal principal) {
        Exam exam = findOrFail(id);
        assertExamAccess(exam, principal);

        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new IllegalArgumentException("Solo se puede finalizar un examen en estado PUBLISHED");
        }
        exam.setStatus("FINISHED");
        return toSummary(examRepository.save(exam));
    }

    // --- PDF Export (B7.12) ---

    public byte[] exportToPdf(Long id) throws IOException {
        Exam exam = findOrFail(id);
        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(id);

        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float y = PDRectangle.A4.getHeight() - margin;
                float lineH = 15;

                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Título
                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText(exam.getTitle());
                cs.endText();
                y -= lineH * 2;

                // Descripción
                if (exam.getDescription() != null) {
                    cs.beginText();
                    cs.setFont(regular, 11);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(exam.getDescription());
                    cs.endText();
                    y -= lineH * 1.5f;
                }

                // Preguntas (sin respuestas correctas)
                int num = 1;
                for (Question q : questions) {
                    if (y < margin + 60) {
                        PDPage newPage = new PDPage(PDRectangle.A4);
                        doc.addPage(newPage);
                        // Note: in a real export we'd open a new content stream for the new page
                        break;
                    }
                    cs.beginText();
                    cs.setFont(bold, 11);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(num++ + ". " + sanitize(q.getQuestionText()));
                    cs.endText();
                    y -= lineH;

                    if (q.getOptions() != null) {
                        char letter = 'A';
                        for (String opt : q.getOptions()) {
                            cs.beginText();
                            cs.setFont(regular, 10);
                            cs.newLineAtOffset(margin + 15, y);
                            cs.showText(letter++ + ") " + sanitize(opt));
                            cs.endText();
                            y -= lineH;
                        }
                    } else {
                        // Espacio en blanco para respuesta libre
                        y -= lineH * 3;
                    }
                    y -= lineH * 0.5f;
                }
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    // --- Helpers ---

    public Exam findOrFail(Long id) {
        return examRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Examen no encontrado con ID: " + id));
    }

    /**
     * TEACHER: solo puede gestionar exámenes que creó él mismo y si tiene canCreateExams.
     * ACADEMY_ADMIN: acceso total.
     */
    public void assertExamAccess(Exam exam, UserPrincipal principal) {
        if ("TEACHER".equals(principal.getRole()) && !exam.getCreatedBy().equals(principal.getId())) {
            throw new AccessDeniedException("No tiene acceso a este examen");
        }
    }

    private void assertDraft(Exam exam, String action) {
        if (!"DRAFT".equals(exam.getStatus())) {
            throw new IllegalArgumentException("No se puede " + action + " un examen que ya no está en estado DRAFT");
        }
    }

    private void validateScope(String scopeType, Long cycleId, Long areaId, Long courseId) {
        switch (scopeType.toUpperCase()) {
            case "CYCLE" -> { if (cycleId == null) throw new IllegalArgumentException("cycleId es requerido para scope CYCLE"); }
            case "AREA"  -> { if (areaId == null)  throw new IllegalArgumentException("areaId es requerido para scope AREA"); }
            case "COURSE"-> { if (courseId == null) throw new IllegalArgumentException("courseId es requerido para scope COURSE"); }
            case "SIMULACRO" -> {
                // SIMULACRO exams do not require cycleId/areaId/courseId — area linking is via exam_area_mapping
            }
            default -> throw new IllegalArgumentException("Tipo de alcance inválido: " + scopeType);
        }
    }

    private void validateQuestionType(String type) {
        if (!List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TEXT", "FILE_UPLOAD").contains(type)) {
            throw new IllegalArgumentException("Tipo de pregunta inválido: " + type);
        }
    }

    /**
     * Convierte IDs de Student (PK) a IDs de User (FK) antes de crear notificaciones.
     * Enrollment almacena studentId (Student.id), pero las notificaciones se consultan
     * por userId (User.id del JWT). Sin esta conversión los estudiantes nunca ven las notifs.
     */
    private void notifyStudentList(List<Long> studentIds, Long academyId, String type, String title, String message) {
        if (studentIds.isEmpty()) return;
        studentRepository.findUserIdsByStudentIds(studentIds)
                .forEach(uid -> notificationService.createForUser(uid, academyId, type, title, message));
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text.replace("\n", " ").replace("\r", "");
    }

    private ExamResponse toResponse(Exam e, List<Question> questions) {
        return new ExamResponse(e.getId(), e.getUuid(), e.getScopeType(),
                e.getCycleId(), e.getAreaId(), e.getCourseId(), e.getCreatedBy(),
                e.getTitle(), e.getDescription(), e.getStatus(),
                e.getScheduledDate(),
                e.getAvailableFrom(), e.getAvailableUntil(),
                e.getDurationMinutes(), e.getMaxAttempts(), e.getPassingScore(),
                e.getIsPublished(),
                questions.stream().map(this::toQuestionResponse).toList(),
                e.getCreatedAt());
    }

    ExamSummaryResponse toSummary(Exam e) {
        return new ExamSummaryResponse(e.getId(), e.getUuid(), e.getScopeType(),
                e.getCycleId(), e.getAreaId(), e.getCourseId(),
                e.getTitle(), e.getStatus(),
                e.getAvailableFrom(), e.getAvailableUntil(),
                e.getDurationMinutes(), e.getMaxAttempts(), e.getPassingScore(),
                e.getCreatedAt());
    }

    QuestionResponse toQuestionResponse(Question q) {
        return new QuestionResponse(q.getId(), q.getUuid(), q.getExamId(),
                q.getQuestionText(), q.getQuestionType(), q.getOptions(),
                q.getCorrectAnswer(), q.getPoints(), q.getOrderIndex(),
                q.getRequiresManualGrading());
    }
}

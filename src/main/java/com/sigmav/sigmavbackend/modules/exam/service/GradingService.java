package com.sigmav.sigmavbackend.modules.exam.service;

import com.sigmav.sigmavbackend.modules.exam.dto.GradeAllRequest;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.entity.Question;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingService {

    private final ExamAttemptRepository attemptRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;

    /**
     * B7.9 — Calificación automática.
     * Califica SINGLE_CHOICE y MULTIPLE_CHOICE. Deja TEXT/FILE_UPLOAD para manual.
     * Si no hay preguntas manuales → status=GRADED de inmediato.
     */
    @Transactional
    public void autoGrade(Long attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Intento no encontrado: " + attemptId));

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new NotFoundException("Examen no encontrado"));

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(attempt.getExamId());

        Map<String, Object> data = attempt.getAnswers() != null ? attempt.getAnswers() : new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> studentAnswers = (Map<String, Object>) data.getOrDefault("answers", new HashMap<>());

        BigDecimal autoScore = BigDecimal.ZERO;
        boolean hasManualQuestions = false;

        for (Question q : questions) {
            if (Boolean.TRUE.equals(q.getRequiresManualGrading())) {
                hasManualQuestions = true;
            } else {
                String qId = q.getId().toString();
                Object studentAnswer = studentAnswers.get(qId);
                if (studentAnswer != null && isCorrectAnswer(q, studentAnswer)) {
                    autoScore = autoScore.add(q.getPoints());
                }
            }
        }

        attempt.setAutoScore(autoScore);

        if (!hasManualQuestions) {
            // Calificación completa: no hay preguntas manuales
            attempt.setTotalScore(autoScore);
            attempt.setPassed(isPassed(autoScore, attempt.getExamId(), exam.getPassingScore()));
            attempt.setStatus("GRADED");
            attempt.setGradedAt(LocalDateTime.now());
        }
        // Si hay preguntas manuales, el intento queda en SUBMITTED para corrección manual

        attemptRepository.save(attempt);
    }

    /**
     * B7.10 — Calificar una pregunta manual de un intento.
     * Si todas las preguntas manuales ya tienen score → finaliza la calificación.
     */
    @Transactional
    public void gradeQuestion(Long attemptId, Long questionId, BigDecimal score,
                              String feedback, Long gradedByUserId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Intento no encontrado: " + attemptId));

        if (!"SUBMITTED".equals(attempt.getStatus())) {
            throw new IllegalArgumentException("Solo se pueden calificar intentos en estado SUBMITTED");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("Pregunta no encontrada: " + questionId));

        if (!question.getExamId().equals(attempt.getExamId())) {
            throw new IllegalArgumentException("La pregunta no pertenece al examen de este intento");
        }
        if (!Boolean.TRUE.equals(question.getRequiresManualGrading())) {
            throw new IllegalArgumentException("Esta pregunta no requiere calificación manual");
        }

        // Guardar score y feedback en el JSONB de answers
        Map<String, Object> data = attempt.getAnswers() != null
                ? new HashMap<>(attempt.getAnswers()) : new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> manualGrades = new HashMap<>(
                (Map<String, Object>) data.getOrDefault("manualGrades", new HashMap<>()));
        manualGrades.put(questionId.toString(), score);
        data.put("manualGrades", manualGrades);

        if (feedback != null && !feedback.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> manualFeedback = new HashMap<>(
                    (Map<String, Object>) data.getOrDefault("manualFeedback", new HashMap<>()));
            manualFeedback.put(questionId.toString(), feedback);
            data.put("manualFeedback", manualFeedback);
        }

        attempt.setAnswers(data);

        // ¿Están calificadas todas las preguntas manuales?
        List<Question> manualQuestions = questionRepository.findByExamIdOrderByOrderIndex(attempt.getExamId())
                .stream().filter(Question::getRequiresManualGrading).toList();

        boolean allGraded = manualQuestions.stream()
                .allMatch(q -> manualGrades.containsKey(q.getId().toString()));

        if (allGraded) {
            BigDecimal manualScore = manualGrades.values().stream()
                    .map(v -> new BigDecimal(v.toString()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Exam exam = examRepository.findById(attempt.getExamId())
                    .orElseThrow(() -> new NotFoundException("Examen no encontrado"));

            BigDecimal auto = attempt.getAutoScore() != null ? attempt.getAutoScore() : BigDecimal.ZERO;
            BigDecimal total = auto.add(manualScore);

            attempt.setManualScore(manualScore);
            attempt.setTotalScore(total);
            attempt.setPassed(isPassed(total, attempt.getExamId(), exam.getPassingScore()));
            attempt.setStatus("GRADED");
            attempt.setGradedBy(gradedByUserId);
            attempt.setGradedAt(LocalDateTime.now());
        }

        attemptRepository.save(attempt);
    }

    /**
     * B7.10 — Calificar TODAS las preguntas manuales de un intento en una sola transacción.
     * Evita el problema de escrituras concurrentes en el JSONB cuando se usan múltiples llamadas paralelas.
     */
    @Transactional
    public void gradeAllQuestions(Long attemptId, GradeAllRequest request, Long gradedByUserId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Intento no encontrado: " + attemptId));

        if (!"SUBMITTED".equals(attempt.getStatus())) {
            throw new IllegalArgumentException("Solo se pueden calificar intentos en estado SUBMITTED");
        }

        List<Question> manualQuestions = questionRepository.findByExamIdOrderByOrderIndex(attempt.getExamId())
                .stream().filter(Question::getRequiresManualGrading).toList();

        // Construir mapa de grade por questionId para validación rápida
        Map<Long, GradeAllRequest.QuestionGrade> gradeMap = new HashMap<>();
        for (GradeAllRequest.QuestionGrade g : request.grades()) {
            gradeMap.put(g.questionId(), g);
        }

        Map<String, Object> data = attempt.getAnswers() != null
                ? new HashMap<>(attempt.getAnswers()) : new HashMap<>();

        Map<String, Object> manualGrades   = new HashMap<>();
        Map<String, Object> manualFeedback = new HashMap<>();

        BigDecimal manualScore = BigDecimal.ZERO;

        for (Question q : manualQuestions) {
            GradeAllRequest.QuestionGrade grade = gradeMap.get(q.getId());
            BigDecimal score = grade != null ? grade.score() : BigDecimal.ZERO;
            manualGrades.put(q.getId().toString(), score);
            manualScore = manualScore.add(score);

            if (grade != null && grade.feedback() != null && !grade.feedback().isBlank()) {
                manualFeedback.put(q.getId().toString(), grade.feedback());
            }
        }

        data.put("manualGrades", manualGrades);
        if (!manualFeedback.isEmpty()) data.put("manualFeedback", manualFeedback);
        attempt.setAnswers(data);

        Exam exam = examRepository.findById(attempt.getExamId())
                .orElseThrow(() -> new NotFoundException("Examen no encontrado"));

        BigDecimal auto  = attempt.getAutoScore() != null ? attempt.getAutoScore() : BigDecimal.ZERO;
        BigDecimal total = auto.add(manualScore);

        attempt.setManualScore(manualScore);
        attempt.setTotalScore(total);
        attempt.setPassed(isPassed(total, attempt.getExamId(), exam.getPassingScore()));
        attempt.setStatus("GRADED");
        attempt.setGradedBy(gradedByUserId);
        attempt.setGradedAt(LocalDateTime.now());

        attemptRepository.save(attempt);
    }

    /**
     * Calcula si el puntaje obtenido supera la nota mínima.
     * passingScore está en porcentaje (0-100), score es puntaje bruto.
     * Fórmula: (score / maxScore) * 100 >= passingScore
     */
    private boolean isPassed(BigDecimal score, Long examId, BigDecimal passingScore) {
        BigDecimal maxScore = examRepository.sumQuestionPoints(examId);
        if (maxScore == null || maxScore.compareTo(BigDecimal.ZERO) == 0) return false;
        BigDecimal percentage = score
                .divide(maxScore, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return percentage.compareTo(passingScore) >= 0;
    }

    @SuppressWarnings("unchecked")
    private Set<String> toStringSet(Object obj) {
        if (obj instanceof List<?> list) {
            Set<String> result = new HashSet<>();
            for (Object item : list) result.add(item.toString());
            return result;
        }
        return Set.of(obj.toString());
    }

    private boolean isCorrectAnswer(Question q, Object studentAnswer) {
        if ("SINGLE_CHOICE".equals(q.getQuestionType()) && q.getCorrectAnswer() != null) {
            return q.getCorrectAnswer().contains(studentAnswer.toString());
        }
        if ("MULTIPLE_CHOICE".equals(q.getQuestionType()) && q.getCorrectAnswer() != null) {
            return toStringSet(studentAnswer).equals(new HashSet<>(q.getCorrectAnswer()));
        }
        return false;
    }
}

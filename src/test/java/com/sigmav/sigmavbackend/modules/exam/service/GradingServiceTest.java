package com.sigmav.sigmavbackend.modules.exam.service;

import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.entity.Question;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// ─────────────────────────────────────────────────────────────────────────────
// @ExtendWith(MockitoExtension.class)
//   Le dice a JUnit 5 que use la extensión de Mockito.
//   Esto activa el procesamiento de @Mock e @InjectMocks automáticamente.
//   SIN Spring: no levanta contexto, no BD, no Redis → muy rápido.
// ─────────────────────────────────────────────────────────────────────────────
@ExtendWith(MockitoExtension.class)
@DisplayName("GradingService — Calificación de exámenes")
class GradingServiceTest {

    // ── MOCKS ────────────────────────────────────────────────────────────────
    // @Mock crea un objeto falso que intercepta todas las llamadas.
    // Por defecto, los métodos retornan null / 0 / false.
    // Con when(...).thenReturn(...) definimos qué debe devolver para cada caso.
    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ExamRepository examRepository;

    // ── CLASE A PROBAR ───────────────────────────────────────────────────────
    // @InjectMocks instancia GradingService e inyecta los @Mock declarados arriba.
    // Es equivalente a: new GradingService(attemptRepository, questionRepository, examRepository)
    @InjectMocks
    private GradingService gradingService;

    // =========================================================================
    // autoGrade — escenarios
    // =========================================================================

    @Test
    @DisplayName("autoGrade: intento no existe → NotFoundException")
    void autoGrade_intentoNoExiste_lanzaNotFoundException() {
        // ARRANGE — configuramos qué devuelve el mock cuando lo consulten
        when(attemptRepository.findById(99L)).thenReturn(Optional.empty());

        // ACT + ASSERT — assertThatThrownBy verifica que se lanza la excepción esperada
        assertThatThrownBy(() -> gradingService.autoGrade(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");

        // VERIFY — confirma que se llamó al repositorio (no cortocircuitó antes)
        verify(attemptRepository).findById(99L);
        // Y que NO se intentó buscar el examen ni preguntas porque falló antes
        verify(examRepository, never()).findById(any());
    }

    @Test
    @DisplayName("autoGrade: respuesta correcta SINGLE_CHOICE → suma los puntos")
    void autoGrade_respuestaCorrectaSingleChoice_sumaPoints() {
        // ARRANGE
        Long examId = 10L;
        Long attemptId = 1L;

        // Pregunta automática: SINGLE_CHOICE, respuesta correcta = "París", vale 10 puntos
        Question pregunta = Question.builder()
                .id(1L)
                .examId(examId)
                .questionType("SINGLE_CHOICE")
                .correctAnswer(List.of("París"))   // respuesta correcta
                .points(new BigDecimal("10.00"))
                .requiresManualGrading(false)
                .build();

        // El estudiante respondió "París" → correcto
        // Estructura JSONB: {"answers": {"1": "París"}}
        Map<String, Object> answers = new HashMap<>();
        answers.put("answers", new HashMap<>(Map.of("1", "París")));

        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId)
                .examId(examId)
                .answers(answers)
                .build();

        Exam exam = Exam.builder()
                .id(examId)
                .passingScore(new BigDecimal("10.00"))
                .build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId)).thenReturn(List.of(pregunta));

        // ACT
        gradingService.autoGrade(attemptId);

        // ASSERT — verificamos el estado del objeto attempt después de calificar
        // isEqualByComparingTo ignora la escala decimal: 10.00 == 10.0 == 10
        assertThat(attempt.getAutoScore()).isEqualByComparingTo("10.00");

        // VERIFY — el repositorio debe haber guardado el intento actualizado
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("autoGrade: respuesta incorrecta → autoScore = 0, no suma puntos")
    void autoGrade_respuestaIncorrecta_noSumaPoints() {
        Long examId = 10L;
        Long attemptId = 1L;

        Question pregunta = Question.builder()
                .id(1L)
                .examId(examId)
                .questionType("SINGLE_CHOICE")
                .correctAnswer(List.of("París"))
                .points(new BigDecimal("10.00"))
                .requiresManualGrading(false)
                .build();

        // El estudiante respondió "Londres" → incorrecto
        Map<String, Object> answers = new HashMap<>();
        answers.put("answers", new HashMap<>(Map.of("1", "Londres")));

        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId).examId(examId).answers(answers).build();

        Exam exam = Exam.builder().id(examId)
                .passingScore(new BigDecimal("50.00")).build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId)).thenReturn(List.of(pregunta));

        gradingService.autoGrade(attemptId);

        assertThat(attempt.getAutoScore()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("autoGrade: solo preguntas automáticas → status cambia a GRADED")
    void autoGrade_soloAutomaticas_marcaGRADED() {
        Long examId = 10L;
        Long attemptId = 1L;

        // Dos preguntas automáticas, ambas respondidas correctamente
        Question q1 = Question.builder()
                .id(1L).examId(examId).questionType("SINGLE_CHOICE")
                .correctAnswer(List.of("A")).points(new BigDecimal("5.00"))
                .requiresManualGrading(false).build();
        Question q2 = Question.builder()
                .id(2L).examId(examId).questionType("SINGLE_CHOICE")
                .correctAnswer(List.of("B")).points(new BigDecimal("5.00"))
                .requiresManualGrading(false).build();

        Map<String, Object> answers = new HashMap<>();
        answers.put("answers", new HashMap<>(Map.of("1", "A", "2", "B")));

        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId).examId(examId).answers(answers).build();

        // passingScore = 8, el estudiante saca 10 → aprueba
        Exam exam = Exam.builder().id(examId)
                .passingScore(new BigDecimal("8.00")).build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId)).thenReturn(List.of(q1, q2));
        // sumQuestionPoints = 10 (5+5); isPassed calcula: (10/10)*100 = 100% >= 8% → true
        when(examRepository.sumQuestionPoints(examId)).thenReturn(new BigDecimal("10.00"));

        gradingService.autoGrade(attemptId);

        // Sin preguntas manuales → debe quedar GRADED
        assertThat(attempt.getStatus()).isEqualTo("GRADED");
        assertThat(attempt.getTotalScore()).isEqualByComparingTo("10.00");
        assertThat(attempt.getPassed()).isTrue();   // (10/10)*100=100% >= 8%
        assertThat(attempt.getGradedAt()).isNotNull();
    }

    @Test
    @DisplayName("autoGrade: hay pregunta manual → NO cambia a GRADED, queda para corrección")
    void autoGrade_conPreguntaManual_quedaEnSUBMITTED() {
        Long examId = 10L;
        Long attemptId = 1L;

        // Una pregunta auto + una pregunta manual (TEXT)
        Question qAuto = Question.builder()
                .id(1L).examId(examId).questionType("SINGLE_CHOICE")
                .correctAnswer(List.of("A")).points(new BigDecimal("5.00"))
                .requiresManualGrading(false).build();
        Question qManual = Question.builder()
                .id(2L).examId(examId).questionType("TEXT")
                .points(new BigDecimal("5.00"))
                .requiresManualGrading(true).build();  // ← requiere corrección manual

        Map<String, Object> answers = new HashMap<>();
        answers.put("answers", new HashMap<>(Map.of("1", "A")));

        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId).examId(examId).answers(answers).build();

        Exam exam = Exam.builder().id(examId)
                .passingScore(new BigDecimal("8.00")).build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId)).thenReturn(List.of(qAuto, qManual));

        gradingService.autoGrade(attemptId);

        // Con preguntas manuales pendientes → NO debe cambiar a GRADED
        // El status queda como estaba (IN_PROGRESS/SUBMITTED), no se toca totalScore ni passed
        assertThat(attempt.getStatus()).isNotEqualTo("GRADED");
        assertThat(attempt.getTotalScore()).isNull(); // no se calcula hasta completar manual
        assertThat(attempt.getPassed()).isNull();

        // Pero sí se calcula el autoScore parcial
        assertThat(attempt.getAutoScore()).isEqualByComparingTo("5.00");
    }

    // =========================================================================
    // gradeQuestion — escenarios
    // =========================================================================

    @Test
    @DisplayName("gradeQuestion: intento no está en SUBMITTED → IllegalArgumentException")
    void gradeQuestion_intentoNoEnSUBMITTED_lanzaIllegalArgument() {
        Long attemptId = 1L;

        // El intento ya fue calificado (GRADED), no se puede volver a calificar
        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId)
                .examId(10L)
                .status("GRADED")   // ← no es SUBMITTED
                .build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() ->
                gradingService.gradeQuestion(attemptId, 1L, new BigDecimal("5"), null, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    @DisplayName("gradeQuestion: pregunta no requiere calificación manual → IllegalArgumentException")
    void gradeQuestion_preguntaNoEsManual_lanzaIllegalArgument() {
        Long examId = 10L;
        Long attemptId = 1L;
        Long questionId = 2L;

        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId).examId(examId).status("SUBMITTED").build();

        // Esta pregunta es de opción múltiple — automática, no manual
        Question pregunta = Question.builder()
                .id(questionId).examId(examId)
                .questionType("SINGLE_CHOICE")
                .requiresManualGrading(false)  // ← no es manual
                .build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(pregunta));

        assertThatThrownBy(() ->
                gradingService.gradeQuestion(attemptId, questionId, new BigDecimal("5"), null, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manual");
    }

    @Test
    @DisplayName("gradeQuestion: pregunta pertenece a otro examen → IllegalArgumentException")
    void gradeQuestion_preguntaDeOtroExamen_lanzaIllegalArgument() {
        Long attemptId = 1L;

        // El intento pertenece al examen 10
        ExamAttempt attempt = ExamAttempt.builder()
                .id(attemptId).examId(10L).status("SUBMITTED").build();

        // La pregunta pertenece al examen 99 (diferente)
        Question pregunta = Question.builder()
                .id(2L).examId(99L)   // ← examen diferente
                .requiresManualGrading(true)
                .build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(questionRepository.findById(2L)).thenReturn(Optional.of(pregunta));

        assertThatThrownBy(() ->
                gradingService.gradeQuestion(attemptId, 2L, new BigDecimal("5"), null, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

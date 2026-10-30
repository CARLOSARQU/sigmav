package com.sigmav.sigmavbackend.modules.exam.service;

import com.sigmav.sigmavbackend.modules.area.repository.AreaRepository;
import com.sigmav.sigmavbackend.modules.course.repository.CourseRepository;
import com.sigmav.sigmavbackend.modules.cycle.repository.CycleRepository;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAttempt;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.QuestionRepository;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.student.service.StudentPortalService;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamAttemptService — SIMULACRO access control")
class ExamAttemptServiceSimulacroTest {

    private static final Long EXAM_ID = 100L;
    private static final Long STUDENT_ID = 200L;
    private static final Long ACADEMY_ID = 1L;

    @Mock
    private ExamAttemptRepository attemptRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamService examService;

    @Mock
    private GradingService gradingService;

    @Mock
    private StudentPortalService studentPortalService;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private CycleRepository cycleRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StorageService storageService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ExamAttemptService examAttemptService;

    private UserPrincipal createPrincipal() {
        return new UserPrincipal(STUDENT_ID, ACADEMY_ID, "student@test.com", "Student", "ROLE_STUDENT", true, null);
    }

    private Exam createSimulacroExam() {
        return Exam.builder()
                .id(EXAM_ID).uuid(UUID.randomUUID()).academyId(ACADEMY_ID)
                .scopeType("SIMULACRO").title("Simulacro de prueba")
                .status("PUBLISHED").durationMinutes(60).maxAttempts(3)
                .passingScore(new java.math.BigDecimal("60"))
                .availableFrom(null).availableUntil(null)
                .build();
    }

    private Student createActiveStudent() {
        return Student.builder()
                .id(STUDENT_ID).academyId(ACADEMY_ID)
                .email("student@test.com").firstName("Estudiuno").lastName("Activo")
                .isActive(true).build();
    }

    // =========================================================================
    // startAttempt — SIMULACRO scope
    // =========================================================================

    @Test
    @DisplayName("startAttempt: SIMULACRO with active student → succeeds, no enrollment check")
    void startAttempt_simulacroActiveStudent_succeeds() {
        // ARRANGE
        Exam exam = createSimulacroExam();
        Student student = createActiveStudent();
        UserPrincipal principal = createPrincipal();

        when(examService.findOrFail(EXAM_ID)).thenReturn(exam);
        when(studentPortalService.resolveStudent(principal)).thenReturn(student);
        when(attemptRepository.countByExamIdAndStudentId(EXAM_ID, STUDENT_ID)).thenReturn(0L);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        ExamAttempt savedAttempt = ExamAttempt.builder()
                .id(1L).academyId(ACADEMY_ID).examId(EXAM_ID).studentId(STUDENT_ID)
                .attemptNumber(1).status("IN_PROGRESS")
                .startedAt(LocalDateTime.now()).build();
        when(attemptRepository.save(any(ExamAttempt.class))).thenReturn(savedAttempt);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // ACT
        var response = examAttemptService.startAttempt(EXAM_ID, principal);

        // ASSERT — success
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);

        // VERIFY — enrollmentRepository was NEVER called (SIMULACRO skips enrollment check)
        verify(enrollmentRepository, never()).existsActiveCourseEnrollment(anyLong(), anyLong());
        verify(enrollmentRepository, never()).existsActiveAreaEnrollment(anyLong(), anyLong());
        verify(enrollmentRepository, never()).findEnrolledCourseIds(anyLong());
        verify(enrollmentRepository, never()).findEnrolledAreaIds(anyLong());
        verify(courseRepository, never()).findById(anyLong());
        verify(courseRepository, never()).findByAreaIdAndAcademyIdAndIsActiveTrueOrderByName(anyLong(), anyLong());
        verify(areaRepository, never()).findByCycleIdAndAcademyIdOrderByName(anyLong(), anyLong());

        // VERIFY — studentRepository was used for the isActive check
        // (hasStudentAccessToExam calls studentRepository.findById for SIMULACRO scope)
        verify(studentRepository).findById(STUDENT_ID);

        // VERIFY — Redis timer was set
        verify(redisTemplate.opsForValue()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("startAttempt: SIMULACRO with inactive student → AccessDeniedException")
    void startAttempt_simulacroInactiveStudent_denied() {
        // ARRANGE
        Exam exam = createSimulacroExam();
        Student inactiveStudent = Student.builder()
                .id(STUDENT_ID).academyId(ACADEMY_ID)
                .email("inactive@test.com").firstName("Estudiuno").lastName("Inactivo")
                .isActive(false).build();
        UserPrincipal principal = createPrincipal();

        when(examService.findOrFail(EXAM_ID)).thenReturn(exam);
        when(studentPortalService.resolveStudent(principal)).thenReturn(inactiveStudent);
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(inactiveStudent));

        // ACT + ASSERT
        assertThatThrownBy(() -> examAttemptService.startAttempt(EXAM_ID, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tienes acceso");

        // VERIFY — attempt was never created
        verify(attemptRepository, never()).save(any());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("startAttempt: SIMULACRO with non-existent student → AccessDeniedException")
    void startAttempt_simulacroNonExistentStudent_denied() {
        // ARRANGE
        Exam exam = createSimulacroExam();
        UserPrincipal principal = createPrincipal();

        when(examService.findOrFail(EXAM_ID)).thenReturn(exam);
        when(studentPortalService.resolveStudent(principal)).thenReturn(
                Student.builder().id(STUDENT_ID).build());
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() -> examAttemptService.startAttempt(EXAM_ID, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tienes acceso");
    }

    // =========================================================================
    // getAvailableExams — SIMULACRO exclusion
    // =========================================================================

    @Test
    @DisplayName("getAvailableExams: filters out SIMULACRO scope exams")
    void getAvailableExams_filtersOutSimulacro() {
        // ARRANGE
        TenantContext.setCurrentAcademyId(ACADEMY_ID);
        Exam simulacroExam = createSimulacroExam();
        Exam regularExam = Exam.builder()
                .id(200L).scopeType("COURSE").courseId(1L)
                .status("PUBLISHED").durationMinutes(60).maxAttempts(1)
                .passingScore(new java.math.BigDecimal("60")).build();

        UserPrincipal principal = createPrincipal();
        Student student = createActiveStudent();

        when(studentPortalService.resolveStudent(principal)).thenReturn(student);
        when(examRepository.findAllPublishedForStudent(anyLong(), any()))
                .thenReturn(List.of(simulacroExam, regularExam));
        when(enrollmentRepository.findEnrolledCourseIds(STUDENT_ID))
                .thenReturn(java.util.Set.of(1L));
        when(enrollmentRepository.findEnrolledAreaIds(STUDENT_ID))
                .thenReturn(java.util.Set.of());
        when(courseRepository.findAllById(java.util.Set.of(1L)))
                .thenReturn(java.util.List.of());

        when(examService.toSummary(regularExam))
                .thenReturn(null); // We don't care about the mapping, just the count

        // ACT
        var result = examAttemptService.getAvailableExams(principal);

        // ASSERT — only 1 exam returned (SIMULACRO filtered out)
        assertThat(result).hasSize(1);

        TenantContext.clear();
    }

    // =========================================================================
    // hasStudentAccessToExam — indirectly tested via startAttempt for SIMULACRO
    // The method is private, but startAssertStudentExamAccess is called by startAttempt.
    // Tests above verify:
    //   ✓ Active student → access granted (no exception)
    //   ✓ Inactive student → AccessDeniedException
    //   ✓ Non-existent student → AccessDeniedException
    // =========================================================================
}

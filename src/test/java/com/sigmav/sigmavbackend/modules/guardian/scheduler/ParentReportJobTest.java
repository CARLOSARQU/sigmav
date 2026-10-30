package com.sigmav.sigmavbackend.modules.guardian.scheduler;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.auth.service.EmailService;
import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAttemptRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.guardian.entity.Guardian;
import com.sigmav.sigmavbackend.modules.guardian.entity.RelationshipType;
import com.sigmav.sigmavbackend.modules.guardian.repository.GuardianRepository;
import com.sigmav.sigmavbackend.modules.session.repository.AttendanceRecordRepository;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParentReportJob — Reporte semanal a apoderados")
class ParentReportJobTest {

    private static final Long ACADEMY_ID = 1L;
    private static final Long STUDENT_ID = 100L;
    private static final Long GUARDIAN_ID = 10L;
    private static final Long GUARDIAN_WITHOUT_EMAIL_ID = 11L;

    @Mock private AcademyRepository academyRepository;
    @Mock private GuardianRepository guardianRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private AttendanceRecordRepository attendanceRecordRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private ExamRepository examRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private ParentReportJob parentReportJob;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Academy createAcademy() {
        Academy academy = Academy.builder()
                .id(ACADEMY_ID)
                .uuid(UUID.randomUUID())
                .name("Academia Test")
                .subdomain("test")
                .isActive(true)
                .build();

        LocalDateTime now = LocalDateTime.now();
        academy.getConfig().put("reportScheduleDay", now.getDayOfWeek().toString());
        academy.getConfig().put("reportScheduleHour", String.valueOf(now.getHour()));

        return academy;
    }

    private Guardian createGuardian(Long id, String email) {
        return Guardian.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(ACADEMY_ID)
                .studentId(STUDENT_ID)
                .fullName("María García")
                .phone("999888777")
                .email(email)
                .relationship(RelationshipType.MADRE)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Student createActiveStudent() {
        return Student.builder()
                .id(STUDENT_ID)
                .uuid(UUID.randomUUID())
                .academyId(ACADEMY_ID)
                .firstName("Juan")
                .lastName("Pérez")
                .dni("12345678")
                .code("STU-001")
                .isActive(true)
                .build();
    }

    // =========================================================================
    // Test cases
    // =========================================================================

    @Test
    @DisplayName("Academy without guardians: skipped, no emails sent")
    void academyWithoutGuardians_skipped() {
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(0L);

        parentReportJob.sendWeeklyReports();

        verify(guardianRepository, never()).findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(anyLong());
        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Multiple academies: first has guardians, second has none")
    void multipleAcademies_secondSkipped() {
        Academy academy1 = createAcademy();
        Academy academy2 = Academy.builder()
                .id(2L).uuid(UUID.randomUUID()).name("Segunda Academia")
                .subdomain("segunda").isActive(true).build();

        LocalDateTime now = LocalDateTime.now();
        academy2.getConfig().put("reportScheduleDay", now.getDayOfWeek().toString());
        academy2.getConfig().put("reportScheduleHour", String.valueOf(now.getHour()));

        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(academy1, academy2));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(1L);
        when(guardianRepository.countByAcademyId(2L)).thenReturn(0L);

        Guardian guardian = createGuardian(GUARDIAN_ID, "maria@test.com");
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(guardian));

        Student student = createActiveStudent();
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(STUDENT_ID)).thenReturn(true);
        when(attendanceRecordRepository.countByStudentIdAndCheckInAtAfter(anyLong(), any())).thenReturn(5L);
        when(attendanceRecordRepository.countAttendedByStudentIdSince(anyLong(), any())).thenReturn(4L);
        when(examAttemptRepository.findByStudentIdOrderByStartedAtDesc(STUDENT_ID)).thenReturn(List.of());

        parentReportJob.sendWeeklyReports();

        verify(emailService).sendHtmlEmail(eq("maria@test.com"), anyString(), anyString());
        verify(guardianRepository, never()).findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(2L);
    }

    @Test
    @DisplayName("Guardian with email: email sent with correct student data")
    void guardianWithEmail_sendsReport() {
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(1L);

        Guardian guardian = createGuardian(GUARDIAN_ID, "maria@test.com");
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(guardian));

        Student student = createActiveStudent();
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(STUDENT_ID)).thenReturn(true);
        when(attendanceRecordRepository.countByStudentIdAndCheckInAtAfter(anyLong(), any())).thenReturn(5L);
        when(attendanceRecordRepository.countAttendedByStudentIdSince(anyLong(), any())).thenReturn(4L);
        when(examAttemptRepository.findByStudentIdOrderByStartedAtDesc(STUDENT_ID)).thenReturn(List.of());

        parentReportJob.sendWeeklyReports();

        verify(emailService).sendHtmlEmail(
                eq("maria@test.com"),
                argThat(subject -> subject.contains("Juan Pérez")),
                argThat(html -> html.contains("Juan Pérez") && html.contains("80%") && html.contains("ACTIVO"))
        );
    }

    @Test
    @DisplayName("Guardian with inactive student: skipped")
    void guardianWithInactiveStudent_skipped() {
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(1L);

        Guardian guardian = createGuardian(GUARDIAN_ID, "maria@test.com");
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(guardian));

        Student inactiveStudent = createActiveStudent();
        inactiveStudent.setIsActive(false);
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.of(inactiveStudent));

        parentReportJob.sendWeeklyReports();

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Guardian with non-existent student: skipped")
    void guardianWithNonExistentStudent_skipped() {
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(1L);

        Guardian guardian = createGuardian(GUARDIAN_ID, "maria@test.com");
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(guardian));

        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        parentReportJob.sendWeeklyReports();

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Guardian without email: filtered by query, never processed")
    void guardianWithoutEmail_notReturnedByQuery() {
        // Guardians without email are filtered by the query method itself.
        // This test verifies they never enter the processing loop.
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(2L);

        Guardian withEmail = createGuardian(GUARDIAN_ID, "maria@test.com");
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(withEmail));

        Student student = createActiveStudent();
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(STUDENT_ID)).thenReturn(false);
        when(attendanceRecordRepository.countByStudentIdAndCheckInAtAfter(anyLong(), any())).thenReturn(0L);
        when(attendanceRecordRepository.countAttendedByStudentIdSince(anyLong(), any())).thenReturn(0L);
        when(examAttemptRepository.findByStudentIdOrderByStartedAtDesc(STUDENT_ID)).thenReturn(List.of());

        parentReportJob.sendWeeklyReports();

        // Only one guardian with email is processed
        verify(emailService).sendHtmlEmail(eq("maria@test.com"), anyString(), anyString());
        // The guardian without email never enters the loop because it's not in the query result
        verify(emailService, times(1)).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Failure in one guardian does not block others")
    void failureInOneGuardian_doesNotBlockOthers() {
        when(academyRepository.findByIsActiveTrue()).thenReturn(List.of(createAcademy()));
        when(guardianRepository.countByAcademyId(ACADEMY_ID)).thenReturn(2L);

        Guardian guardian1 = createGuardian(GUARDIAN_ID, "maria@test.com");
        Guardian guardian2 = createGuardian(GUARDIAN_ID + 1, "juan@test.com");
        guardian2.setStudentId(STUDENT_ID + 1);
        when(guardianRepository.findByAcademyIdAndIsActiveTrueAndEmailIsNotNull(ACADEMY_ID))
                .thenReturn(List.of(guardian1, guardian2));

        Student student1 = createActiveStudent();
        Student student2 = createActiveStudent();
        student2.setId(STUDENT_ID + 1);
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID, ACADEMY_ID))
                .thenReturn(Optional.of(student1));
        when(studentRepository.findByIdAndAcademyId(STUDENT_ID + 1, ACADEMY_ID))
                .thenThrow(new RuntimeException("DB connection lost"));

        when(enrollmentRepository.existsActiveEnrollmentForStudent(STUDENT_ID)).thenReturn(true);
        when(attendanceRecordRepository.countByStudentIdAndCheckInAtAfter(anyLong(), any())).thenReturn(3L);
        when(attendanceRecordRepository.countAttendedByStudentIdSince(anyLong(), any())).thenReturn(2L);
        when(examAttemptRepository.findByStudentIdOrderByStartedAtDesc(STUDENT_ID)).thenReturn(List.of());

        parentReportJob.sendWeeklyReports();

        // First guardian receives email despite second one failing
        verify(emailService).sendHtmlEmail(eq("maria@test.com"), anyString(), anyString());
        // Second guardian does not receive email (exception before sending)
        verify(emailService, never()).sendHtmlEmail(eq("juan@test.com"), anyString(), anyString());
    }
}

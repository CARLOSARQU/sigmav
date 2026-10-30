package com.sigmav.sigmavbackend.modules.student.service;

import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.guardian.service.GuardianService;
import com.sigmav.sigmavbackend.modules.student.dto.CreateStudentRequest;
import com.sigmav.sigmavbackend.modules.student.dto.StudentResponse;
import com.sigmav.sigmavbackend.modules.student.dto.UpdateStudentRequest;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentService — Gestión de estudiantes")
class StudentServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private GuardianService guardianService;

    @InjectMocks private StudentService studentService;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentAcademyId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: email ya existe globalmente → IllegalArgumentException")
    void create_emailDuplicadoGlobal_lanzaException() {
        when(studentRepository.existsByDniAndAcademyId("12345678", 1L)).thenReturn(false);
        when(studentRepository.countByAcademyId(1L)).thenReturn(0L);
        when(studentRepository.existsByCodeAndAcademyId(anyString(), eq(1L))).thenReturn(false);
        when(userRepository.existsByEmail("dup@otra.com")).thenReturn(true);

        CreateStudentRequest req = new CreateStudentRequest("Ana", "García", "12345678", "dup@otra.com", null, null, "pass123", null);

        assertThatThrownBy(() -> studentService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup@otra.com");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: email null → no llama existsByEmail y crea el estudiante")
    void create_emailNulo_noVerificaEmail() {
        when(studentRepository.existsByDniAndAcademyId("12345678", 1L)).thenReturn(false);
        when(studentRepository.countByAcademyId(1L)).thenReturn(0L);
        when(studentRepository.existsByCodeAndAcademyId(anyString(), eq(1L))).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(buildUser(1L));
        when(studentRepository.save(any())).thenReturn(buildStudent(10L, "Ana", "García", null));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(10L)).thenReturn(false);
        when(guardianService.findByStudentId(anyLong(), anyLong())).thenReturn(List.of());

        CreateStudentRequest req = new CreateStudentRequest("Ana", "García", "12345678", null, null, null, "pass123", null);
        studentService.create(req);

        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("create: contraseña menor de 6 caracteres → IllegalArgumentException")
    void create_passwordCorta_lanzaException() {
        when(studentRepository.existsByDniAndAcademyId("12345678", 1L)).thenReturn(false);
        when(studentRepository.countByAcademyId(1L)).thenReturn(0L);
        when(studentRepository.existsByCodeAndAcademyId(anyString(), eq(1L))).thenReturn(false);
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);

        CreateStudentRequest req = new CreateStudentRequest("Ana", "García", "12345678", "ana@test.com", null, null, "123", null);

        assertThatThrownBy(() -> studentService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 caracteres");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: sin contraseña → se autogenera y aparece en la respuesta")
    void create_sinPassword_autogeneraYRetornaGeneratedPassword() {
        when(studentRepository.existsByDniAndAcademyId("12345678", 1L)).thenReturn(false);
        when(studentRepository.countByAcademyId(1L)).thenReturn(0L);
        when(studentRepository.existsByCodeAndAcademyId(anyString(), eq(1L))).thenReturn(false);
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(buildUser(1L));
        when(studentRepository.save(any())).thenReturn(buildStudent(10L, "Ana", "García", "ana@test.com"));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(10L)).thenReturn(false);
        when(guardianService.findByStudentId(anyLong(), anyLong())).thenReturn(List.of());

        CreateStudentRequest req = new CreateStudentRequest("Ana", "García", "12345678", "ana@test.com", null, null, null, null);
        StudentResponse resp = studentService.create(req);

        assertThat(resp.generatedPassword())
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("create: con contraseña válida → generatedPassword es null en la respuesta")
    void create_conPasswordValida_generatedPasswordEsNull() {
        when(studentRepository.existsByDniAndAcademyId("12345678", 1L)).thenReturn(false);
        when(studentRepository.countByAcademyId(1L)).thenReturn(0L);
        when(studentRepository.existsByCodeAndAcademyId(anyString(), eq(1L))).thenReturn(false);
        when(userRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(buildUser(1L));
        when(studentRepository.save(any())).thenReturn(buildStudent(10L, "Ana", "García", "ana@test.com"));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(10L)).thenReturn(false);
        when(guardianService.findByStudentId(anyLong(), anyLong())).thenReturn(List.of());

        CreateStudentRequest req = new CreateStudentRequest("Ana", "García", "12345678", "ana@test.com", null, null, "secreto123", null);
        StudentResponse resp = studentService.create(req);

        assertThat(resp.generatedPassword()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: nuevo email en uso, tiene userId → usa existsByEmailAndIdNot → exception")
    void update_emailEnUso_conUserId_lanzaException() {
        Student student = buildStudent(10L, "Ana", "García", "ana@test.com");
        student.setUserId(5L);
        when(studentRepository.findByIdAndAcademyId(10L, 1L)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmailAndIdNot("otro@test.com", 5L)).thenReturn(true);

        UpdateStudentRequest req = new UpdateStudentRequest(null, null, null, "otro@test.com", null, null, null);

        assertThatThrownBy(() -> studentService.update(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro@test.com");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: nuevo email en uso, sin userId → usa existsByEmail → exception")
    void update_emailEnUso_sinUserId_lanzaException() {
        Student student = buildStudent(10L, "Ana", "García", "ana@test.com");
        // userId es null — rama del existsByEmail global
        when(studentRepository.findByIdAndAcademyId(10L, 1L)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmail("otro@test.com")).thenReturn(true);

        UpdateStudentRequest req = new UpdateStudentRequest(null, null, null, "otro@test.com", null, null, null);

        assertThatThrownBy(() -> studentService.update(10L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro@test.com");

        verify(studentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: nuevo email disponible → actualiza email del estudiante")
    void update_emailDisponible_actualizaEstudiante() {
        Student student = buildStudent(10L, "Ana", "García", "ana@test.com");
        student.setUserId(5L);
        when(studentRepository.findByIdAndAcademyId(10L, 1L)).thenReturn(Optional.of(student));
        when(userRepository.existsByEmailAndIdNot("nueva@test.com", 5L)).thenReturn(false);
        when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(5L)).thenReturn(Optional.of(buildUser(5L)));
        when(userRepository.save(any())).thenReturn(buildUser(5L));
        when(enrollmentRepository.existsActiveEnrollmentForStudent(10L)).thenReturn(false);
        when(guardianService.findByStudentId(anyLong(), anyLong())).thenReturn(List.of());

        UpdateStudentRequest req = new UpdateStudentRequest(null, null, null, "nueva@test.com", null, null, null);
        StudentResponse resp = studentService.update(10L, req);

        assertThat(resp.email()).isEqualTo("nueva@test.com");
    }

    @Test
    @DisplayName("update: student belongs to another academy → 404 Not Found and no write")
    void update_studentFromAnotherAcademy_throwsNotFoundAndDoesNotWrite() {
        // The row IS visible to the unscoped finder, so only a tenant-scoped lookup
        // can hide it. A 404 is returned instead of a 403 so the API does not leak
        // that the row exists in another academy.
        Student foreignStudent = buildStudent(99L, "Otra", "Academia", "otra@test.com");
        foreignStudent.setAcademyId(2L);
        foreignStudent.setUserId(null);

        // Stubs that only the buggy (unscoped) implementation would reach: marking
        // them lenient keeps them from being reported as unnecessary.
        lenient().when(studentRepository.findById(99L)).thenReturn(Optional.of(foreignStudent));
        lenient().when(studentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(studentRepository.findByIdAndAcademyId(99L, 1L)).thenReturn(Optional.empty());

        UpdateStudentRequest req = new UpdateStudentRequest("Ana", null, null, null, null, null, null);

        assertThatThrownBy(() -> studentService.update(99L, req))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");

        verify(studentRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id) {
        return User.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(1L)
                .fullName("Ana García")
                .email("ana@test.com")
                .passwordHash("hashed")
                .role("STUDENT")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Student buildStudent(Long id, String firstName, String lastName, String email) {
        return Student.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(1L)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .dni("12345678")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

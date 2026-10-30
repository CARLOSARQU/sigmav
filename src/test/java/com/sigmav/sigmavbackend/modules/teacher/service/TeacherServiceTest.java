package com.sigmav.sigmavbackend.modules.teacher.service;

import com.sigmav.sigmavbackend.modules.teacher.dto.CreateTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.dto.TeacherResponse;
import com.sigmav.sigmavbackend.modules.teacher.dto.UpdateTeacherRequest;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherService — Gestión de docentes")
class TeacherServiceTest {

    private static final String CACHE_NAME = "user-principals";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @InjectMocks private TeacherService teacherService;

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
    @DisplayName("create: email ya existe en otra academia → IllegalArgumentException")
    void create_emailDuplicadoGlobal_lanzaException() {
        when(userRepository.existsByEmail("dup@otra.com")).thenReturn(true);

        CreateTeacherRequest req = new CreateTeacherRequest("Juan Pérez", null, "dup@otra.com", "pass123");

        assertThatThrownBy(() -> teacherService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup@otra.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: contraseña menor de 6 caracteres → IllegalArgumentException")
    void create_passwordCorta_lanzaException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        CreateTeacherRequest req = new CreateTeacherRequest("Juan Pérez", null, "juan@test.com", "123");

        assertThatThrownBy(() -> teacherService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 caracteres");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: sin contraseña → se autogenera y aparece en la respuesta")
    void create_sinPassword_autogeneraYRetornaGeneratedPassword() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(buildUser(1L, "Ana López", "ana@test.com"));

        CreateTeacherRequest req = new CreateTeacherRequest("Ana López", null, "ana@test.com", null);
        TeacherResponse resp = teacherService.create(req);

        assertThat(resp.generatedPassword())
                .isNotNull()
                .hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("create: con contraseña válida → generatedPassword es null en la respuesta")
    void create_conPasswordValida_generatedPasswordEsNull() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(buildUser(1L, "Pedro", "pedro@test.com"));

        CreateTeacherRequest req = new CreateTeacherRequest("Pedro", null, "pedro@test.com", "secreto123");
        TeacherResponse resp = teacherService.create(req);

        assertThat(resp.generatedPassword()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: nuevo email usado por otro usuario global → IllegalArgumentException")
    void update_emailEnUsoGlobal_lanzaException() {
        User teacher = buildUser(5L, "Laura", "laura@test.com");
        when(userRepository.findByIdAndAcademyId(5L, 1L)).thenReturn(Optional.of(teacher));
        when(userRepository.existsByEmailAndIdNot("otro@test.com", 5L)).thenReturn(true);

        UpdateTeacherRequest req = new UpdateTeacherRequest(null, null, "otro@test.com", null);

        assertThatThrownBy(() -> teacherService.update(5L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("otro@test.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: mismo email que ya tiene → no verifica conflicto ni falla")
    void update_mismoEmail_noVerificaConflicto() {
        User teacher = buildUser(5L, "Laura", "laura@test.com");
        when(userRepository.findByIdAndAcademyId(5L, 1L)).thenReturn(Optional.of(teacher));
        when(userRepository.save(any())).thenReturn(teacher);

        UpdateTeacherRequest req = new UpdateTeacherRequest(null, null, "laura@test.com", null);
        teacherService.update(5L, req);

        verify(userRepository, never()).existsByEmailAndIdNot(anyString(), anyLong());
    }

    @Test
    @DisplayName("update: nuevo email disponible → actualiza correctamente")
    void update_emailDisponible_actualizaDocente() {
        User teacher = buildUser(5L, "Laura", "laura@test.com");
        when(userRepository.findByIdAndAcademyId(5L, 1L)).thenReturn(Optional.of(teacher));
        when(userRepository.existsByEmailAndIdNot("nueva@test.com", 5L)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTeacherRequest req = new UpdateTeacherRequest(null, null, "nueva@test.com", null);
        TeacherResponse resp = teacherService.update(5L, req);

        assertThat(resp.email()).isEqualTo("nueva@test.com");
    }

    // ── deactivate ────────────────────────────────────────────────────────────
    // The cached UserPrincipal carries isActive, so a deactivation that does not
    // evict keeps the teacher fully authorized until the 5-minute TTL expires.

    @Test
    @DisplayName("deactivate: desactiva, guarda y desaloja el principal cacheado del docente")
    void deactivate_evictsCachedPrincipalOfDeactivatedTeacher() {
        User teacher = buildUser(5L, "Laura", "laura@test.com");
        when(userRepository.findByIdAndAcademyId(5L, 1L)).thenReturn(Optional.of(teacher));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // lenient() keeps the failure mode a clean "wanted but not invoked" on the
        // eviction instead of an UnnecessaryStubbing error if production drops it.
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        teacherService.deactivate(5L);

        assertThat(teacher.getIsActive()).isFalse();
        verify(userRepository).save(teacher);
        verify(cacheManager).getCache(CACHE_NAME);
        verify(cache).evict(5L);
    }

    @Test
    @DisplayName("deactivate: desaloja después del save para no repoblar la caché con datos pre-commit")
    void deactivate_evictsAfterSave() {
        User teacher = buildUser(5L, "Laura", "laura@test.com");
        when(userRepository.findByIdAndAcademyId(5L, 1L)).thenReturn(Optional.of(teacher));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        teacherService.deactivate(5L);

        InOrder inOrder = inOrder(userRepository, cacheManager, cache);
        inOrder.verify(userRepository).save(teacher);
        inOrder.verify(cacheManager).getCache(CACHE_NAME);
        inOrder.verify(cache).evict(5L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, String fullName, String email) {
        return User.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(1L)
                .fullName(fullName)
                .email(email)
                .passwordHash("hashed")
                .role("TEACHER")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

package com.sigmav.sigmavbackend.modules.auth.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginRequest;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginResponse;
import com.sigmav.sigmavbackend.modules.auth.repository.ActiveSessionRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.JwtTokenProvider;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — Login y gestión de sesiones")
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private AcademyRepository academyRepository;
    @Mock private ActiveSessionRepository activeSessionRepository;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    // =========================================================================
    // login — escenarios de error
    // =========================================================================

    @Test
    @DisplayName("login: credenciales incorrectas → BadCredentialsException")
    void login_credencialesInvalidas_lanzaBadCredentialsException() {
        // El AuthenticationManager lanza una excepción cuando las credenciales fallan.
        // Spring Security puede lanzar varios tipos (BadCredentialsException, UsernameNotFoundException).
        // El AuthService los captura todos y relanza como BadCredentialsException genérico.
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        LoginRequest request = new LoginRequest("noexiste@mail.com", "claveIncorrecta", null);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Credenciales inválidas");

        // Si el auth falla, NUNCA debe tocar la BD de academias ni generar tokens
        verify(academyRepository, never()).findById(any());
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("login: academia desactivada → BadCredentialsException con mensaje descriptivo")
    void login_academiaInactiva_lanzaBadCredentialsException() {
        Long academyId = 5L;

        // Creamos un UserPrincipal real (no mock) porque AuthService usa instanceof para castearlo
        UserPrincipal principal = new UserPrincipal(
                1L, academyId, "admin@test.com", "hashed", "ACADEMY_ADMIN", true, null
        );

        // Mockeamos Authentication — la interfaz que devuelve el AuthenticationManager
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        // La academia existe pero está INACTIVA (suspendida por falta de pago, etc.)
        Academy academiaInactiva = Academy.builder()
                .id(academyId)
                .isActive(false)   // ← suspendida
                .name("Academia Test")
                .build();
        when(academyRepository.findById(academyId)).thenReturn(Optional.of(academiaInactiva));

        LoginRequest request = new LoginRequest("admin@test.com", "pass123", null);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "Chrome"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("activa");  // mensaje menciona que no está activa

        // Si la academia está inactiva, no se deben generar tokens
        verify(jwtTokenProvider, never()).generateToken(any());
    }

    // =========================================================================
    // login — escenario exitoso: SUPER_ADMIN
    // =========================================================================

    @Test
    @DisplayName("login: SUPER_ADMIN exitoso → retorna LoginResponse sin academyId")
    void login_superAdmin_exitoso_retornaLoginResponse() {
        // SUPER_ADMIN no pertenece a ninguna academia → academyId = null
        // Por eso el Hibernate filter NO se activa para ellos (acceso global)
        UserPrincipal principal = new UserPrincipal(
                99L, null, "super@sigmav.com", "hashed", "SUPER_ADMIN", true, null
        );

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        // Mocks para generación de tokens
        when(jwtTokenProvider.generateToken(principal)).thenReturn("jwt-token-abc");
        when(jwtTokenProvider.generateRefreshToken(principal)).thenReturn("refresh-token-xyz");

        // El servicio busca el nombre completo del usuario para incluirlo en la respuesta
        User superAdmin = User.builder()
                .id(99L)
                .fullName("Super Administrador")
                .email("super@sigmav.com")
                .build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(superAdmin));

        // ACT
        LoginRequest request = new LoginRequest("super@sigmav.com", "pass", null);
        LoginResponse response = authService.login(request, "10.0.0.1", "Firefox");

        // ASSERT
        assertThat(response.token()).isEqualTo("jwt-token-abc");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-xyz");
        assertThat(response.email()).isEqualTo("super@sigmav.com");
        assertThat(response.fullName()).isEqualTo("Super Administrador");
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(response.academyId()).isNull(); // SUPER_ADMIN no tiene academia

        // VERIFY: SUPER_ADMIN no genera active_session (solo STUDENT lo hace)
        verify(activeSessionRepository, never()).save(any());
        // VERIFY: SUPER_ADMIN no consulta la tabla de academias (academyId = null)
        verify(academyRepository, never()).findById(any());
    }

    // =========================================================================
    // login — escenario exitoso: STUDENT (sesión única)
    // =========================================================================

    @Test
    @DisplayName("login: STUDENT exitoso → crea active_session (sesión única)")
    void login_student_exitoso_creaActiveSession() {
        Long academyId = 1L;

        // STUDENT pertenece a una academia específica
        UserPrincipal principal = new UserPrincipal(
                10L, academyId, "student@test.com", "hashed", "STUDENT", true, null
        );

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        // La academia existe y está activa
        Academy academiaActiva = Academy.builder()
                .id(academyId).isActive(true).name("Academia Test").build();
        // Stubbing lenient: se llama 2 veces en login (validación + features)
        when(academyRepository.findById(academyId)).thenReturn(Optional.of(academiaActiva));

        when(jwtTokenProvider.generateToken(principal)).thenReturn("student-jwt");
        when(jwtTokenProvider.generateRefreshToken(principal)).thenReturn("student-refresh");

        User student = User.builder().id(10L).fullName("Juan Pérez").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginRequest request = new LoginRequest("student@test.com", "pass", null);
        LoginResponse response = authService.login(request, "192.168.1.1", "Mozilla/5.0 Chrome/120");

        assertThat(response.token()).isEqualTo("student-jwt");
        assertThat(response.role()).isEqualTo("STUDENT");

        // VERIFY: para STUDENT sí se crea la active_session (garantía de sesión única)
        // any() = no nos importa el objeto exacto, solo que se haya llamado
        verify(activeSessionRepository).save(any());
        // Y se eliminaron las sesiones anteriores del estudiante
        verify(activeSessionRepository).deleteByUserId(10L);
    }

    // =========================================================================
    // logout
    // =========================================================================

    @Test
    @DisplayName("logout: elimina la sesión activa del usuario")
    void logout_eliminaActiveSession() {
        Long userId = 10L;

        authService.logout(userId);

        // Verificamos que se llamó exactamente con el userId correcto
        verify(activeSessionRepository).deleteByUserId(userId);
    }
}

package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.CustomRoleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the role-update privilege boundaries.
 *
 * The role guard lives in the service (not only in the controller) so it cannot
 * be bypassed by any other call path.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — role updates and privilege boundaries")
class UserServiceTest {

    private static final Long ACADEMY_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CustomRoleRepository customRoleRepository;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("updateRole: caller changes their own role → 403 FORBIDDEN and no write")
    void updateRole_selfRoleChange_throwsForbidden() {
        UserPrincipal caller = caller(10L, "ACADEMY_ADMIN");

        assertThatThrownBy(() -> userService.updateRole(10L, "ACADEMIC_COORDINATOR", caller))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRole: non SUPER_ADMIN caller assigns SUPER_ADMIN → 403 FORBIDDEN and no write")
    void updateRole_nonSuperAdminAssignsSuperAdmin_throwsForbidden() {
        UserPrincipal caller = caller(10L, "ACADEMY_ADMIN");

        assertThatThrownBy(() -> userService.updateRole(20L, "SUPER_ADMIN", caller))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRole: SUPER_ADMIN caller assigns SUPER_ADMIN → allowed")
    void updateRole_superAdminAssignsSuperAdmin_succeeds() {
        User target = buildUser(20L, "TEACHER");
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenReturn(target);

        userService.updateRole(20L, "SUPER_ADMIN", caller(10L, "SUPER_ADMIN"));

        assertThat(target.getRole()).isEqualTo("SUPER_ADMIN");
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("updateRole: ordinary role change TEACHER → ACADEMIC_COORDINATOR → still works")
    void updateRole_ordinaryRoleChange_succeeds() {
        User target = buildUser(20L, "TEACHER");
        when(userRepository.findById(20L)).thenReturn(Optional.of(target));
        when(userRepository.save(any())).thenReturn(target);

        userService.updateRole(20L, "ACADEMIC_COORDINATOR", caller(10L, "ACADEMY_ADMIN"));

        assertThat(target.getRole()).isEqualTo("ACADEMIC_COORDINATOR");
        verify(userRepository).save(target);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserPrincipal caller(Long id, String role) {
        return new UserPrincipal(
                id, ACADEMY_ID, "caller@test.com", "hashed", role, true, Set.of());
    }

    private User buildUser(Long id, String role) {
        return User.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(ACADEMY_ID)
                .fullName("Target User")
                .email("target@test.com")
                .passwordHash("hashed")
                .role(role)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

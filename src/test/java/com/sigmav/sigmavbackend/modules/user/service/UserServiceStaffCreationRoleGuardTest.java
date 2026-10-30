package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.permission.service.PermissionService;
import com.sigmav.sigmavbackend.modules.user.dto.CreateStaffUserRequest;
import com.sigmav.sigmavbackend.modules.user.dto.StaffUserCreatedResponse;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Staff creation must refuse a custom role that is currently deactivated.
 *
 * <p>Why this is a guard and not a cosmetic check: {@code PermissionRepository} resolves
 * custom-role grants only while {@code custom_roles.is_active = true}, so a user created
 * against a deactivated role keeps the role name and resolves to zero permissions. Nothing
 * fails loudly at creation time — the account is created, the admin reads a success, and the
 * user is silently locked out. The sibling assignment path
 * ({@code UserPermissionController.updateRole}) already refuses an inactive role, so before
 * this guard the two paths disagreed about the same input.
 *
 * <p>The guard lives in {@link UserService}, next to the other privilege boundaries it
 * already enforces (self role-change and SUPER_ADMIN granting), so a caller that never goes
 * through the controller is covered too.
 *
 * <p>Runs against the same Postgres as the Spring context; the transaction rolls back.
 * Fixtures are created here with {@link JdbcTemplate} so the test states its own
 * preconditions instead of inheriting them from seeded data.
 *
 * <p>Writes nothing to the shared {@code user-principals} Redis cache: the permission
 * assertions go through {@link PermissionService#getEffectivePermissions}, which reads the
 * database directly. Only {@code CustomUserDetailsService.loadUserById} caches a principal,
 * and it is not exercised here.
 */
@SpringBootTest
@Transactional
@DisplayName("UserService.createStaffUser — a deactivated custom role is not assignable")
class UserServiceStaffCreationRoleGuardTest {

    private static final String DEACTIVATED_ROLE = "Deactivated Supervision";
    private static final String ACTIVE_ROLE = "Active Supervision";
    private static final String GRANTED_PERMISSION = "VIEW_REPORTS";

    /** The exact refusal the sibling assignment path already produces for the same input. */
    private static final String INACTIVE_ROLE_MESSAGE =
            "Role '" + DEACTIVATED_ROLE + "' is currently inactive and cannot be assigned";

    @Autowired private UserService userService;
    @Autowired private PermissionService permissionService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long academyId;

    @BeforeEach
    void createAcademy() {
        academyId = insertAcademy();
    }

    // ── the hole ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a deactivated custom role is rejected with 400 and no user is created")
    void deactivatedCustomRoleIsRejected() {
        insertCustomRole(academyId, DEACTIVATED_ROLE, false);

        String email = staffEmail();
        assertThatThrownBy(() -> userService.createStaffUser(staffRequest(email, DEACTIVATED_ROLE), academyId))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo(INACTIVE_ROLE_MESSAGE);
                });

        // Fail closed means nothing is written: no half-created account to clean up.
        assertThat(userRepository.existsByEmail(email))
                .as("a rejected creation must not leave a user behind")
                .isFalse();
    }

    // ── the happy path must not regress ───────────────────────────────────────

    @Test
    @DisplayName("an active custom role is still assignable and its grants resolve")
    void activeCustomRoleIsStillAssignable() {
        Long roleId = insertCustomRole(academyId, ACTIVE_ROLE, true);
        grantToCustomRole(roleId, GRANTED_PERMISSION);

        StaffUserCreatedResponse created =
                userService.createStaffUser(staffRequest(staffEmail(), ACTIVE_ROLE), academyId);

        assertThat(created.role()).isEqualTo(ACTIVE_ROLE);
        assertThat(userRepository.existsByEmail(created.email())).isTrue();
        assertThat(permissionService.getEffectivePermissions(created.id(), academyId))
                .as("the active role's grants reach the new user")
                .containsExactly(GRANTED_PERMISSION);
    }

    // ── the consequence the guard prevents ────────────────────────────────────

    @Test
    @DisplayName("deactivating the role of a freshly created staff user leaves them with no permission at all")
    void holderOfADeactivatedRoleResolvesNothing() {
        Long roleId = insertCustomRole(academyId, ACTIVE_ROLE, true);
        grantToCustomRole(roleId, GRANTED_PERMISSION);

        StaffUserCreatedResponse created =
                userService.createStaffUser(staffRequest(staffEmail(), ACTIVE_ROLE), academyId);
        assertThat(permissionService.getEffectivePermissions(created.id(), academyId))
                .containsExactly(GRANTED_PERMISSION);

        deactivate(roleId);

        // Pins why creation-time validation matters: the role staying assigned is enough to
        // take the whole set away, and nothing anywhere reports it. Deactivation is a
        // deliberate act on an existing role; creating a user already locked out is not.
        assertThat(permissionService.getEffectivePermissions(created.id(), academyId))
                .as("assigned to a deactivated role: zero permissions, no error")
                .isEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private CreateStaffUserRequest staffRequest(String email, String role) {
        return new CreateStaffUserRequest("New Staff Member", email, role, "TempPass123", null);
    }

    private String staffEmail() {
        return "staff-" + UUID.randomUUID() + "@test.com";
    }

    private Long insertAcademy() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO academies (name, subdomain) VALUES (?, ?) RETURNING id",
                Long.class, "Academy Staff Guard", "staff-guard-" + UUID.randomUUID());
    }

    private Long insertCustomRole(Long academyId, String name, boolean isActive) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO custom_roles (academy_id, name, is_active) VALUES (?, ?, ?) RETURNING id",
                Long.class, academyId, name, isActive);
    }

    private void grantToCustomRole(Long customRoleId, String permissionName) {
        jdbcTemplate.update(
                "INSERT INTO custom_role_permissions (custom_role_id, permission_name) VALUES (?, ?)",
                customRoleId, permissionName);
    }

    private void deactivate(Long customRoleId) {
        jdbcTemplate.update("UPDATE custom_roles SET is_active = false WHERE id = ?", customRoleId);
    }
}

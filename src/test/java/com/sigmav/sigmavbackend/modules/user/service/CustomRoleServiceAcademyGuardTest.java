package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.user.dto.CreateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.dto.CustomRoleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A custom role is always owned by exactly one academy, and creation must say so.
 *
 * <p>The owning academy is what makes the role reachable: {@code PermissionRepository}
 * resolves custom grants by joining {@code users} to {@code custom_roles} on the
 * {@code (name, academy_id)} pair, so a role without an academy can never match a user and
 * its permissions could never be resolved by anyone. Creation therefore fails closed rather
 * than accepting an argument the rest of the system cannot honour.
 *
 * <p>The reachable caller is a SUPER_ADMIN: {@code CustomRoleController.createRole} forwards
 * {@code principal.getAcademyId()} untouched, and the SUPER_ADMIN principal is built without
 * an academy ({@code CustomUserDetailsService}), so the null arrives from the request context
 * rather than from a malformed body. The guard sits in {@link CustomRoleService} so every
 * caller inherits it, present and future, instead of the controller restating it.
 *
 * <p>Runs against the same Postgres as the Spring context; the transaction rolls back.
 * Writes nothing to the shared {@code user-principals} Redis cache.
 */
@SpringBootTest
@Transactional
@DisplayName("CustomRoleService.create — a custom role always belongs to an academy")
class CustomRoleServiceAcademyGuardTest {

    private static final String ROLE_NAME = "Academy-less Supervision";
    private static final String GRANTED_PERMISSION = "VIEW_REPORTS";

    private static final String NO_ACADEMY_MESSAGE =
            "Cannot create a custom role without an academy: a custom role is always owned by exactly one academy";

    @Autowired private CustomRoleService customRoleService;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ── the hole ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a null academy is rejected with 400 and nothing is written")
    void nullAcademyIsRejected() {
        assertThatThrownBy(() -> customRoleService.create(null,
                new CreateCustomRoleRequest(ROLE_NAME, null, List.of(GRANTED_PERMISSION))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo(NO_ACADEMY_MESSAGE);
                });

        // Fail closed: the refusal happens before any row, so there is no orphan role and no
        // grant set attached to it.
        assertThat(roleCountByName(ROLE_NAME))
                .as("a rejected creation must not leave a role behind")
                .isZero();
    }

    // ── the happy path must not regress ───────────────────────────────────────

    @Test
    @DisplayName("a valid academy still creates the role with its grants")
    void validAcademyStillCreatesTheRole() {
        Long academyId = insertAcademy();

        CustomRoleResponse created = customRoleService.create(academyId,
                new CreateCustomRoleRequest(ROLE_NAME, "Supervision at one academy", List.of(GRANTED_PERMISSION)));

        assertThat(created.academyId()).isEqualTo(academyId);
        assertThat(created.isActive()).isTrue();
        assertThat(created.permissions()).containsExactly(GRANTED_PERMISSION);
        assertThat(academyIdOf(created.id())).isEqualTo(academyId);
        assertThat(customRoleService.getById(created.id(), academyId).permissions())
                .containsExactly(GRANTED_PERMISSION);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Long insertAcademy() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO academies (name, subdomain) VALUES (?, ?) RETURNING id",
                Long.class, "Academy Role Guard", "role-guard-" + UUID.randomUUID());
    }

    private long roleCountByName(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM custom_roles WHERE name = ?", Long.class, name);
    }

    private Long academyIdOf(Long customRoleId) {
        return jdbcTemplate.queryForObject(
                "SELECT academy_id FROM custom_roles WHERE id = ?", Long.class, customRoleId);
    }
}

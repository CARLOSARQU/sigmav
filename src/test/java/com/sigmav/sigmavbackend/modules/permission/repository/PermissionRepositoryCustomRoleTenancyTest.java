package com.sigmav.sigmavbackend.modules.permission.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation for custom-role permissions, exercised against a real database.
 *
 * <p>The permission resolution and the rewrite statements are native SQL: only a real
 * Postgres can prove which rows they touch. Mocking the repository would assert that the
 * mock was called, which is precisely the property that was broken — the old query joined
 * {@code users} to {@code role_permissions} on role <em>name</em> alone, so two academies
 * owning a role with the same name shared one set of permission rows.
 *
 * <p>Built-in grants (ACADEMY_ADMIN, TEACHER, ...) are global by nature and stay shared.
 * Tenant-owned grants are keyed on {@code custom_roles.id}, which is unique per academy,
 * so tenancy is derived from the role row instead of duplicated onto every grant.
 *
 * <p>Runs against the same Postgres as the Spring context; the transaction rolls back, so
 * it leaves no trace. Fixtures are created here rather than relying on seeded data.
 */
@SpringBootTest
@Transactional
@DisplayName("PermissionRepository — custom role permissions are scoped to their academy")
class PermissionRepositoryCustomRoleTenancyTest {

    /** Deliberately owned by both academies: the name collision is the defect under test. */
    private static final String SHARED_ROLE_NAME = "Supervision";

    private static final String PERMISSION_OF_ACADEMY_A = "VIEW_REPORTS";
    private static final String PERMISSION_OF_ACADEMY_B = "READ_FINANCES";

    @Autowired private PermissionRepository permissionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long academyA;
    private Long academyB;
    private Long userA;
    private Long userB;
    private Long roleA;
    private Long roleB;

    @BeforeEach
    void createFixtures() {
        academyA = insertAcademy();
        academyB = insertAcademy();

        userA = insertUser(academyA, SHARED_ROLE_NAME);
        userB = insertUser(academyB, SHARED_ROLE_NAME);

        roleA = insertCustomRole(academyA, SHARED_ROLE_NAME);
        roleB = insertCustomRole(academyB, SHARED_ROLE_NAME);

        grantToCustomRole(roleA, PERMISSION_OF_ACADEMY_A);
        grantToCustomRole(roleB, PERMISSION_OF_ACADEMY_B);
    }

    // ── read isolation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a user resolves only the grants of the same-named role owned by their own academy")
    void sameNamedRolesResolveToTheirOwnAcademyGrants() {
        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA))
                .containsExactly(PERMISSION_OF_ACADEMY_A);
        assertThat(permissionRepository.findEffectivePermissionsForUser(userB, academyB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
    }

    @Test
    @DisplayName("a role name owned by another academy is not a path to its permissions")
    void anotherAcademysRoleNameGrantsNothing() {
        // A user in academy A holding a role name that only academy B owns has no custom
        // grants at all: the name alone must never resolve, only the (name, academy) pair.
        Long outsider = insertUser(academyA, "RoleOnlyAcademyBOwns");
        jdbcTemplate.update("INSERT INTO custom_roles (academy_id, name) VALUES (?, ?)",
                academyB, "RoleOnlyAcademyBOwns");

        assertThat(permissionRepository.findEffectivePermissionsForUser(outsider, academyA)).isEmpty();
    }

    @Test
    @DisplayName("built-in role grants stay global and resolve for any academy")
    void builtInGrantsResolveRegardlessOfAcademy() {
        Long teacherInA = insertUser(academyA, "TEACHER");
        Long teacherInB = insertUser(academyB, "TEACHER");

        // Built-in grants have no owning academy, so both users see the same seeded set.
        assertThat(permissionRepository.findEffectivePermissionsForUser(teacherInA, academyA))
                .contains("EDIT_GRADES", "MANAGE_ATTENDANCE", "MANAGE_EXAMS", "VIEW_ATTENDANCE", "VIEW_REPORTS");
        assertThat(permissionRepository.findEffectivePermissionsForUser(teacherInB, academyB))
                .contains("EDIT_GRADES", "MANAGE_ATTENDANCE", "MANAGE_EXAMS", "VIEW_ATTENDANCE", "VIEW_REPORTS");
    }

    @Test
    @DisplayName("every built-in role still resolves exactly its seeded grants")
    void builtInGrantsResolveUnchanged() {
        // Regression guard for the custom branch's is_active predicate: built-in rows have
        // no custom_roles row and no is_active concept, so if the filter ever reached this
        // branch the built-in grants would silently disappear. Expected values are read
        // from the source table, so this covers all 37 seeded rows and survives seed edits.
        int resolvedInTotal = 0;

        for (String builtInRole : List.of(
                "ACADEMY_ADMIN", "ACADEMIC_COORDINATOR", "TEACHER", "SALES_ADVISOR", "COLLECTIONS_MANAGER")) {
            List<String> seeded = jdbcTemplate.queryForList(
                    "SELECT permission_name FROM role_permissions WHERE role = ?", String.class, builtInRole);
            assertThat(seeded).as("seeded grants for %s", builtInRole).isNotEmpty();

            Long holder = insertUser(academyA, builtInRole);
            assertThat(permissionRepository.findEffectivePermissionsForUser(holder, academyA))
                    .as("resolved grants for %s", builtInRole)
                    .containsExactlyInAnyOrderElementsOf(seeded);

            resolvedInTotal += seeded.size();
        }

        assertThat(resolvedInTotal).as("the 37 built-in rows are all covered here").isEqualTo(37);
    }

    // ── a deactivated role grants nothing ─────────────────────────────────────

    @Test
    @DisplayName("a user holding a deactivated custom role resolves nothing from it")
    void deactivatedCustomRoleGrantsNothing() {
        deactivate(roleA);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA)).isEmpty();
    }

    @Test
    @DisplayName("a deactivated custom role does not suppress the user's own overrides")
    void deactivatedCustomRoleStillHonoursOverrides() {
        deactivate(roleA);
        grantOverride(userA, academyA, "MANAGE_STUDENTS");

        // Overrides are a separate source: deactivating the role removes the role's grants,
        // not the grants this user was individually given.
        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA))
                .containsExactly("MANAGE_STUDENTS");
    }

    @Test
    @DisplayName("deactivating one academy's role leaves the other academy's same-named role granting")
    void deactivationIsScopedToItsOwnRole() {
        deactivate(roleA);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA)).isEmpty();
        assertThat(permissionRepository.findEffectivePermissionsForUser(userB, academyB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
    }

    @Test
    @DisplayName("reactivating a custom role restores its grants")
    void reactivatedCustomRoleGrantsAgain() {
        deactivate(roleA);
        jdbcTemplate.update("UPDATE custom_roles SET is_active = true WHERE id = ?", roleA);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA))
                .containsExactly(PERMISSION_OF_ACADEMY_A);
    }

    // ── write isolation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleting one role's permissions leaves the other academy's same-named role intact")
    void deleteByCustomRoleIdDoesNotReachTheOtherAcademy() {
        permissionRepository.deleteCustomRolePermissions(roleA);

        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleA)).isEmpty();
        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
        assertThat(permissionRepository.findEffectivePermissionsForUser(userB, academyB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA)).isEmpty();
    }

    @Test
    @DisplayName("granting to one role does not leak into the other academy's same-named role")
    void insertByCustomRoleIdDoesNotReachTheOtherAcademy() {
        permissionRepository.insertCustomRolePermission(roleA, "MANAGE_SIMULACROS");

        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleA))
                .containsExactlyInAnyOrder(PERMISSION_OF_ACADEMY_A, "MANAGE_SIMULACROS");
        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
        assertThat(permissionRepository.findEffectivePermissionsForUser(userB, academyB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
    }

    @Test
    @DisplayName("a rename in one academy does not move the other academy's grants")
    void renameDoesNotTouchPermissionRows() {
        // The key is the stable custom_roles.id, so renaming a role is a pure custom_roles
        // update: no permission row is rewritten under any name.
        jdbcTemplate.update("UPDATE custom_roles SET name = ? WHERE id = ?", "Supervision Renamed", roleA);

        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleA))
                .containsExactly(PERMISSION_OF_ACADEMY_A);
        assertThat(permissionRepository.findPermissionsByCustomRoleId(roleB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
        assertThat(permissionRepository.findEffectivePermissionsForUser(userB, academyB))
                .containsExactly(PERMISSION_OF_ACADEMY_B);
    }

    // ── the union/except shape is preserved ───────────────────────────────────

    @Test
    @DisplayName("a revoked override subtracts from the custom role grants")
    void revokedOverrideWinsOverCustomRoleGrants() {
        jdbcTemplate.update("""
                INSERT INTO user_permission_overrides (user_id, permission_name, academy_id, granted)
                VALUES (?, ?, ?, false)
                """, userA, PERMISSION_OF_ACADEMY_A, academyA);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA)).isEmpty();
    }

    @Test
    @DisplayName("a granted override adds to the custom role grants")
    void grantedOverrideAddsToCustomRoleGrants() {
        jdbcTemplate.update("""
                INSERT INTO user_permission_overrides (user_id, permission_name, academy_id, granted)
                VALUES (?, ?, ?, true)
                """, userA, "MANAGE_STUDENTS", academyA);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA))
                .containsExactlyInAnyOrder(PERMISSION_OF_ACADEMY_A, "MANAGE_STUDENTS");
    }

    @Test
    @DisplayName("an override granted in another academy does not apply here")
    void overrideFromAnotherAcademyIsIgnored() {
        jdbcTemplate.update("""
                INSERT INTO user_permission_overrides (user_id, permission_name, academy_id, granted)
                VALUES (?, ?, ?, true)
                """, userA, "MANAGE_STUDENTS", academyB);

        assertThat(permissionRepository.findEffectivePermissionsForUser(userA, academyA))
                .containsExactly(PERMISSION_OF_ACADEMY_A);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Long insertAcademy() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO academies (name, subdomain) VALUES (?, ?) RETURNING id",
                Long.class, "Academy Tenancy", "tenancy-" + UUID.randomUUID());
    }

    private Long insertUser(Long academyId, String role) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (academy_id, email, password_hash, full_name, role)
                VALUES (?, ?, 'hashed', ?, ?) RETURNING id
                """, Long.class, academyId, "tenancy-" + UUID.randomUUID() + "@test.com", "Tenant User", role);
    }

    private Long insertCustomRole(Long academyId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO custom_roles (academy_id, name) VALUES (?, ?) RETURNING id",
                Long.class, academyId, name);
    }

    private void grantToCustomRole(Long customRoleId, String permissionName) {
        jdbcTemplate.update(
                "INSERT INTO custom_role_permissions (custom_role_id, permission_name) VALUES (?, ?)",
                customRoleId, permissionName);
    }

    private void deactivate(Long customRoleId) {
        jdbcTemplate.update("UPDATE custom_roles SET is_active = false WHERE id = ?", customRoleId);
    }

    private void grantOverride(Long userId, Long academyId, String permissionName) {
        jdbcTemplate.update("""
                INSERT INTO user_permission_overrides (user_id, permission_name, academy_id, granted)
                VALUES (?, ?, ?, true)
                """, userId, permissionName, academyId);
    }
}

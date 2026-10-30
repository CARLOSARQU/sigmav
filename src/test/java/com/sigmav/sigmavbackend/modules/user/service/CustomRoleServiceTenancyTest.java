package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.user.dto.CreateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.dto.UpdateCustomRoleRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation for the custom-role write path, against a real database.
 *
 * <p>The service rewrites grants through native statements, so only a real Postgres can
 * show which rows a role update reaches. The defect this guards against: two academies
 * each owning a role named {@code Supervision}, where updating one rewrote or deleted the
 * other's grants because the rows were addressed by role <em>name</em> — a global
 * namespace. Addressing them by {@code custom_roles.id} makes that impossible by
 * construction, which is what these tests measure.
 *
 * <p>Runs against the same Postgres as the Spring context; the transaction rolls back.
 */
@SpringBootTest
@Transactional
@DisplayName("CustomRoleService — a role update reaches only its own academy's grants")
class CustomRoleServiceTenancyTest {

    private static final String SHARED_ROLE_NAME = "Supervision";

    @Autowired private CustomRoleService customRoleService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CacheManager cacheManager;

    private Long academyA;
    private Long academyB;
    private Long userB;
    private Long roleA;
    private Long roleB;

    /**
     * Cache keys this test wrote into the shared {@code user-principals} cache, undone by
     * {@link #evictProbeKeys()} whether or not the test passed.
     */
    private final List<Long> probeKeys = new ArrayList<>();

    @BeforeEach
    void createFixtures() {
        academyA = insertAcademy();
        academyB = insertAcademy();

        userB = insertUser(academyB, SHARED_ROLE_NAME);

        roleA = insertCustomRole(academyA, SHARED_ROLE_NAME);
        roleB = insertCustomRole(academyB, SHARED_ROLE_NAME);

        grantToCustomRole(roleA, "VIEW_REPORTS");
        grantToCustomRole(roleB, "READ_FINANCES");
    }

    @Test
    @DisplayName("replacing one academy's grants leaves the other academy's same-named role intact")
    void updateInOneAcademyDoesNotRewriteTheOtherAcademysGrants() {
        customRoleService.update(roleA, academyA,
                new UpdateCustomRoleRequest(null, null, null, List.of("MANAGE_SIMULACROS")));

        assertThat(permissionsOf(roleA)).containsExactly("MANAGE_SIMULACROS");
        assertThat(permissionsOf(roleB)).containsExactly("READ_FINANCES");
        assertThat(customRoleService.getById(roleB, academyB).permissions())
                .containsExactly("READ_FINANCES");
    }

    @Test
    @DisplayName("renaming one academy's role leaves the other academy's same-named role intact")
    void renameInOneAcademyDoesNotMoveTheOtherAcademysGrants() {
        customRoleService.update(roleA, academyA,
                new UpdateCustomRoleRequest("Supervision Renamed", null, null, null));

        // Renaming is a pure custom_roles update: the grants follow the id, so they are
        // never rewritten under any name.
        assertThat(permissionsOf(roleA)).containsExactly("VIEW_REPORTS");
        assertThat(permissionsOf(roleB)).containsExactly("READ_FINANCES");
        assertThat(customRoleService.getById(roleB, academyB).permissions())
                .containsExactly("READ_FINANCES");
    }

    @Test
    @DisplayName("a same-named role held by another academy does not block this academy's rename")
    void renameIsNotBlockedByAnotherAcademysHolders() {
        // userB holds "Supervision" in academy B. That is a different tenant's assignment
        // and must not be counted by this academy's rename guard.
        customRoleService.update(roleA, academyA,
                new UpdateCustomRoleRequest("Supervision Renamed", null, null, null));

        assertThat(customRoleService.getById(roleA, academyA).name()).isEqualTo("Supervision Renamed");
        assertThat(customRoleService.getById(roleB, academyB).name()).isEqualTo(SHARED_ROLE_NAME);
    }

    @Test
    @DisplayName("creating a same-named role in another academy grants nothing here")
    void createInAnotherAcademyDoesNotGrantHere() {
        // No academy owns the name globally, so this must succeed rather than collide.
        Long academyC = insertAcademy();
        Long roleC = customRoleService.create(academyC,
                new CreateCustomRoleRequest(SHARED_ROLE_NAME, null, List.of("MANAGE_STUDENTS"))).id();

        assertThat(roleC).isNotNull();
        assertThat(permissionsOf(roleC)).containsExactly("MANAGE_STUDENTS");
        assertThat(permissionsOf(roleA)).containsExactly("VIEW_REPORTS");
        assertThat(permissionsOf(roleB)).containsExactly("READ_FINANCES");
    }

    @Test
    @DisplayName("listing one academy's roles never reports another academy's grants")
    void listIsScopedToTheAcademy() {
        assertThat(customRoleService.list(academyA))
                .singleElement()
                .satisfies(role -> {
                    assertThat(role.name()).isEqualTo(SHARED_ROLE_NAME);
                    assertThat(role.permissions()).containsExactly("VIEW_REPORTS");
                });
        assertThat(customRoleService.list(academyB))
                .singleElement()
                .satisfies(role -> assertThat(role.permissions()).containsExactly("READ_FINANCES"));
    }

    // ── eviction on deactivation ──────────────────────────────────────────────

    @Test
    @DisplayName("deactivating a role releases its holders' cached principals and nobody else's")
    void isActiveOnlyUpdateEvictsHolders() {
        // A holder in the role's own academy. This is created here rather than in the
        // shared fixture so the rename guards, which count this academy's holders, stay
        // on their zero-holder path for the other tests.
        Long holder = insertUser(academyA, SHARED_ROLE_NAME);
        // Control: the same role name in the other academy. Nothing about academy A's
        // deactivation changes it, so it must survive — which also proves Redis really
        // round-trips, so the eviction below cannot pass because the cache is broken.
        Long bystander = insertUser(academyB, SHARED_ROLE_NAME);

        Cache cache = cacheManager.getCache("user-principals");
        putProbe(cache, holder, "stale principal");
        putProbe(cache, bystander, "unrelated principal");

        // isActive alone: no permission list is sent, yet the effective grants change from
        // "the role's whole set" to "nothing", so the stale principal must not survive.
        customRoleService.update(roleA, academyA,
                new UpdateCustomRoleRequest(null, null, false, null));

        assertThat(cache.get(bystander)).as("another academy's holder is untouched").isNotNull();
        assertThat(cache.get(holder)).as("holder was evicted on deactivation").isNull();
    }

    @Test
    @DisplayName("re-sending the current isActive value evicts nobody")
    void isActiveUnchangedEvictsNobody() {
        Long holder = insertUser(academyA, SHARED_ROLE_NAME);

        Cache cache = cacheManager.getCache("user-principals");
        putProbe(cache, holder, "current principal");

        customRoleService.update(roleA, academyA,
                new UpdateCustomRoleRequest(null, null, true, null));

        assertThat(cache.get(holder)).as("no change means no eviction").isNotNull();
    }

    // ── cache hygiene ─────────────────────────────────────────────────────────

    /**
     * Writes a probe into the real cache, tracking the key first so a probe can never be
     * written without a matching eviction queued.
     */
    private void putProbe(Cache cache, Long userId, String value) {
        probeKeys.add(userId);
        cache.put(userId, value);
    }

    /**
     * Removes the probes this test wrote from the real {@code user-principals} cache, so a
     * full run leaves Redis as it found it. Runs after every test, passing or failing, which
     * is why the tests queue their probes as they write them.
     *
     * <p>That cache is shared Redis and {@code CustomUserDetailsService.loadUserById} reads it
     * in production keyed by user id, so a surviving probe would be read as a principal as
     * soon as the dev {@code users_id_seq} hands that id to a real user — the ids here come
     * from the same sequence, so they name no real user today, but they will. Each key is
     * evicted individually rather than by flushing: the cache is shared with the running dev
     * app, and {@code user-principals::6} holds a real user's principal.
     */
    @AfterEach
    void evictProbeKeys() {
        Cache cache = cacheManager.getCache("user-principals");
        probeKeys.forEach(cache::evict);
        probeKeys.clear();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private List<String> permissionsOf(Long customRoleId) {
        return jdbcTemplate.queryForList(
                "SELECT permission_name FROM custom_role_permissions WHERE custom_role_id = ?",
                String.class, customRoleId);
    }

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
}

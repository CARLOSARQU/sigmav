package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.permission.repository.PermissionRepository;
import com.sigmav.sigmavbackend.modules.user.dto.CustomRoleResponse;
import com.sigmav.sigmavbackend.modules.user.dto.UpdateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.entity.CustomRole;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.CustomRoleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cache-invalidation contract for custom role updates.
 *
 * The cached UserPrincipal carries the user's effective permissions, so rewriting a
 * role's grant set without evicting leaves every holder on the old set until the
 * 5-minute TTL expires. The role-level path is the one that was missed: per-user
 * overrides already evict in PermissionService.
 *
 * <p>Scoping note: grants are keyed on the role's immutable id, so these tests assert
 * the id-keyed calls and the academy-scoped holder lookup. That a real query honours
 * the academy is a property of the SQL, proven against a real database in
 * {@code PermissionRepositoryCustomRoleTenancyTest} — a mocked repository cannot
 * witness it.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomRoleService — role permission rewrite and cache eviction")
class CustomRoleServiceTest {

    private static final String CACHE_NAME = "user-principals";
    private static final Long ACADEMY_ID = 1L;
    private static final Long ROLE_ID = 7L;
    private static final String ROLE_NAME = "COORDINADOR";

    @Mock private CustomRoleRepository customRoleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @InjectMocks private CustomRoleService customRoleService;

    // ── permission rewrite ────────────────────────────────────────────────────

    @Test
    @DisplayName("update: replacing permissions deletes the old grants and inserts the new ones by role id")
    void update_withPermissions_replacesRolePermissions() {
        stubRoleUpdate(List.of());

        CustomRoleResponse response = customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, null, List.of("EDIT_GRADES")));

        verify(permissionRepository).deleteCustomRolePermissions(ROLE_ID);
        verify(permissionRepository).insertCustomRolePermission(ROLE_ID, "EDIT_GRADES");
        assertThat(response.permissions()).containsExactly("EDIT_GRADES");
    }

    @Test
    @DisplayName("update: a rename with new permissions still writes them against the same role id")
    void update_renameWithPermissions_writesAgainstTheSameRoleId() {
        stubRoleUpdate(List.of());
        when(customRoleRepository.existsByNameAndAcademyId("NUEVO", ACADEMY_ID)).thenReturn(false);
        when(userRepository.countByRoleAndAcademyId(ROLE_NAME, ACADEMY_ID)).thenReturn(0L);
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest("NUEVO", null, null, List.of("EDIT_GRADES")));

        // The id is stable across the rename, so the grant set is rewritten under the same
        // key — never under a name, which is what used to reach other academies' rows.
        verify(permissionRepository).deleteCustomRolePermissions(ROLE_ID);
        verify(permissionRepository).insertCustomRolePermission(ROLE_ID, "EDIT_GRADES");
    }

    // ── eviction ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: replacing permissions evicts every holder of the role in the role's academy")
    void update_withPermissions_evictsEveryHolderInTheAcademy() {
        stubRoleUpdate(List.of(
                buildUser(11L, ACADEMY_ID),
                buildUser(12L, ACADEMY_ID)));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, null, List.of("EDIT_GRADES")));

        verify(userRepository).findByRoleAndAcademyIdOrderByFullName(ROLE_NAME, ACADEMY_ID);
        verify(cache).evict(11L);
        verify(cache).evict(12L);
        verify(cache, never()).evict(99L);
    }

    @Test
    @DisplayName("update: eviction looks up holders within the role's academy, not globally")
    void update_withPermissions_scopesTheHolderLookupToTheAcademy() {
        stubRoleUpdate(List.of(buildUser(11L, ACADEMY_ID)));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, null, List.of("EDIT_GRADES")));

        // The grant is tenant-owned, so only this academy's holders changed. A same-named
        // role in another academy keeps its own grants and must not be evicted.
        verify(userRepository).findByRoleAndAcademyIdOrderByFullName(ROLE_NAME, ACADEMY_ID);
        verify(userRepository, never()).findByRoleOrderByFullName(anyString());
    }

    @Test
    @DisplayName("update: evicts after the permission rows are rewritten")
    void update_withPermissions_evictsAfterTheRewrite() {
        stubRoleUpdate(List.of(buildUser(11L, ACADEMY_ID)));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, null, List.of("EDIT_GRADES")));

        InOrder inOrder = inOrder(permissionRepository, cacheManager, cache);
        inOrder.verify(permissionRepository).deleteCustomRolePermissions(ROLE_ID);
        inOrder.verify(permissionRepository).insertCustomRolePermission(ROLE_ID, "EDIT_GRADES");
        inOrder.verify(cacheManager).getCache(CACHE_NAME);
        inOrder.verify(cache).evict(11L);
    }

    @Test
    @DisplayName("update: a role held by zero users still applies and evicts nothing")
    void update_roleHeldByNoUsers_appliesWithoutEvicting() {
        stubRoleUpdate(List.of());
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        CustomRoleResponse response = customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, null, List.of("EDIT_GRADES")));

        assertThat(response.permissions()).containsExactly("EDIT_GRADES");
        verify(permissionRepository).insertCustomRolePermission(ROLE_ID, "EDIT_GRADES");
        verify(cache, never()).evict(anyLong());
        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("update: a no-op update with unchanged permissions does not evict")
    void update_withoutPermissionChange_doesNotEvict() {
        stubRoleUpdate(List.of(buildUser(11L, ACADEMY_ID)));
        when(permissionRepository.findPermissionsByCustomRoleId(ROLE_ID)).thenReturn(List.of("EDIT_GRADES"));

        CustomRoleResponse response = customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, "New description", null, null));

        assertThat(response.permissions()).containsExactly("EDIT_GRADES");
        verify(permissionRepository, never()).deleteCustomRolePermissions(anyLong());
        verify(permissionRepository, never()).insertCustomRolePermission(anyLong(), anyString());
        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("update: deactivating a role evicts its holders even though no permission list was sent")
    void update_isActiveOnlyChange_evictsHolders() {
        stubRoleUpdate(List.of(buildUser(11L, ACADEMY_ID)));
        when(permissionRepository.findPermissionsByCustomRoleId(ROLE_ID)).thenReturn(List.of("EDIT_GRADES"));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, false, null));

        // The role's grants change from "its whole set" to "nothing" without a single
        // permission row moving, so the gate cannot key on the permission list alone.
        verify(cache).evict(11L);
    }

    @Test
    @DisplayName("update: re-sending the current isActive value does not evict")
    void update_isActiveUnchanged_doesNotEvict() {
        // buildRole() is active, so isActive=true is a no-op for the stored state.
        stubRoleUpdate(List.of(buildUser(11L, ACADEMY_ID)));
        when(permissionRepository.findPermissionsByCustomRoleId(ROLE_ID)).thenReturn(List.of("EDIT_GRADES"));

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest(null, null, true, null));

        verifyNoInteractions(cacheManager);
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: rename blocked by assigned users → 409, no permission write, no eviction")
    void update_renameBlockedByAssignedUsers_throwsWithoutEvicting() {
        CustomRole role = buildRole();
        when(customRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(customRoleRepository.existsByNameAndAcademyId("NUEVO", ACADEMY_ID)).thenReturn(false);
        when(userRepository.countByRoleAndAcademyId(ROLE_NAME, ACADEMY_ID)).thenReturn(3L);

        assertThatThrownBy(() -> customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest("NUEVO", null, null, List.of("EDIT_GRADES"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));

        verify(permissionRepository, never()).deleteCustomRolePermissions(anyLong());
        verify(permissionRepository, never()).insertCustomRolePermission(anyLong(), anyString());
        verify(customRoleRepository, never()).save(any());
        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("update: a rename alone keeps the grants and evicts nobody")
    void update_renameWithoutPermissions_keepsGrantsAndEvictsNothing() {
        CustomRole role = buildRole();
        when(customRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        when(customRoleRepository.existsByNameAndAcademyId("NUEVO", ACADEMY_ID)).thenReturn(false);
        when(userRepository.countByRoleAndAcademyId(ROLE_NAME, ACADEMY_ID)).thenReturn(0L);
        when(customRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(permissionRepository.findPermissionsByCustomRoleId(ROLE_ID)).thenReturn(List.of("EDIT_GRADES"));

        CustomRoleResponse response = customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest("NUEVO", null, null, null));

        // Grants follow the id, so nothing about them changes and there is nothing to evict.
        assertThat(response.permissions()).containsExactly("EDIT_GRADES");
        verify(permissionRepository, never()).deleteCustomRolePermissions(anyLong());
        verify(permissionRepository, never()).insertCustomRolePermission(anyLong(), anyString());
        verify(userRepository, never()).findByRoleAndAcademyIdOrderByFullName(anyString(), anyLong());
        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("update: rename with permissions evicts the academy's holders under the new name")
    void update_renameWithPermissions_evictsTheAcademysHoldersUnderTheNewName() {
        stubRoleUpdate(List.of());
        when(customRoleRepository.existsByNameAndAcademyId("NUEVO", ACADEMY_ID)).thenReturn(false);
        when(userRepository.countByRoleAndAcademyId(ROLE_NAME, ACADEMY_ID)).thenReturn(0L);
        when(userRepository.findByRoleAndAcademyIdOrderByFullName("NUEVO", ACADEMY_ID))
                .thenReturn(List.of(buildUser(22L, ACADEMY_ID)));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        customRoleService.update(ROLE_ID, ACADEMY_ID,
                new UpdateCustomRoleRequest("NUEVO", null, null, List.of("EDIT_GRADES")));

        // Only the new name is resolved, and only inside this academy: the old name is no
        // longer a key anything is stored under, so its holders are not a category.
        verify(userRepository).findByRoleAndAcademyIdOrderByFullName("NUEVO", ACADEMY_ID);
        verify(userRepository, never()).findByRoleAndAcademyIdOrderByFullName(ROLE_NAME, ACADEMY_ID);
        verify(userRepository, never()).findByRoleOrderByFullName(anyString());
        verify(cache).evict(22L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Role exists in the academy and no user holds the role. */
    private void stubRoleUpdate(List<User> holders) {
        when(customRoleRepository.findById(ROLE_ID)).thenReturn(Optional.of(buildRole()));
        when(customRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // lenient() keeps the failure mode a clean "wanted but not invoked" on the eviction
        // instead of an UnnecessaryStubbing error when production drops the eviction.
        lenient().when(userRepository.findByRoleAndAcademyIdOrderByFullName(ROLE_NAME, ACADEMY_ID))
                .thenReturn(holders);
    }

    private CustomRole buildRole() {
        return CustomRole.builder()
                .id(ROLE_ID)
                .uuid(UUID.randomUUID())
                .academyId(ACADEMY_ID)
                .name(ROLE_NAME)
                .description("Custom coordinator role")
                .isActive(true)
                .build();
    }

    private User buildUser(Long id, Long academyId) {
        return User.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(academyId)
                .fullName("User " + id)
                .email("user" + id + "@test.com")
                .passwordHash("hashed")
                .role(ROLE_NAME)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

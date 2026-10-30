package com.sigmav.sigmavbackend.modules.superadmin.service;

import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademySubscriptionRepository;
import com.sigmav.sigmavbackend.modules.superadmin.dto.UpdateUserRequest;
import com.sigmav.sigmavbackend.modules.superadmin.dto.UserSummaryResponse;
import com.sigmav.sigmavbackend.modules.user.entity.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminService — Gestión global de usuarios")
class SuperAdminServiceTest {

    private static final String CACHE_NAME = "user-principals";

    @Mock private AcademyRepository academyRepository;
    @Mock private AcademySubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @InjectMocks private SuperAdminService superAdminService;

    // ── updateUser ────────────────────────────────────────────────────────────
    // updateUser mutates three fields the cached UserPrincipal carries — role,
    // isActive and academyId — so every successful update must evict the entry or
    // the stale principal stays authoritative until the 5-minute TTL expires.

    @Test
    @DisplayName("updateUser: cambio de rol desaloja el principal cacheado")
    void updateUser_roleChange_evictsCachedPrincipal() {
        User user = buildUser(7L, 1L, "ACADEMY_ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // lenient() keeps the failure mode a clean "wanted but not invoked" on the
        // eviction instead of an UnnecessaryStubbing error if production drops it.
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        UserSummaryResponse resp = superAdminService.updateUser(7L, new UpdateUserRequest("teacher", null, null));

        assertThat(resp.role()).isEqualTo("TEACHER");
        verify(userRepository).save(user);
        verify(cacheManager).getCache(CACHE_NAME);
        verify(cache).evict(7L);
    }

    @Test
    @DisplayName("updateUser: cambio de isActive desaloja el principal cacheado")
    void updateUser_isActiveChange_evictsCachedPrincipal() {
        User user = buildUser(7L, 1L, "ACADEMY_ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        UserSummaryResponse resp = superAdminService.updateUser(7L, new UpdateUserRequest(null, false, null));

        assertThat(resp.isActive()).isFalse();
        verify(cache).evict(7L);
    }

    @Test
    @DisplayName("updateUser: cambio de academia desaloja el principal cacheado (no el id de la academia)")
    void updateUser_academyIdChange_evictsCachedPrincipal() {
        User user = buildUser(7L, 1L, "ACADEMY_ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(academyRepository.existsById(2L)).thenReturn(true);
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        UserSummaryResponse resp = superAdminService.updateUser(7L, new UpdateUserRequest(null, null, 2L));

        assertThat(resp.academyId()).isEqualTo(2L);
        // The evicted key must be the user id, never the new academy id.
        verify(cache).evict(7L);
    }

    @Test
    @DisplayName("updateUser: sin campos modificados también desaloja (evict incondicional)")
    void updateUser_noFieldChange_stillEvictsCachedPrincipal() {
        User user = buildUser(7L, 1L, "ACADEMY_ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        superAdminService.updateUser(7L, new UpdateUserRequest(null, null, null));

        verify(cache).evict(7L);
    }

    @Test
    @DisplayName("updateUser: desaloja después del save para no repoblar la caché con datos pre-commit")
    void updateUser_evictsAfterSave() {
        User user = buildUser(7L, 1L, "ACADEMY_ADMIN");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);

        superAdminService.updateUser(7L, new UpdateUserRequest("teacher", null, null));

        InOrder inOrder = inOrder(userRepository, cacheManager, cache);
        inOrder.verify(userRepository).save(user);
        inOrder.verify(cacheManager).getCache(CACHE_NAME);
        inOrder.verify(cache).evict(7L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(Long id, Long academyId, String role) {
        return User.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(academyId)
                .fullName("Admin Academia")
                .email("admin@test.com")
                .passwordHash("hashed")
                .role(role)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

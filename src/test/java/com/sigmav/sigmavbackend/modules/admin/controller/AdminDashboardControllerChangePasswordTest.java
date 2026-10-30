package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.modules.admin.dto.ChangePasswordRequest;
import com.sigmav.sigmavbackend.modules.admin.service.AdminDashboardService;
import com.sigmav.sigmavbackend.modules.public_.service.PublicBrandingService;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.modules.user.entity.Role;
import com.sigmav.sigmavbackend.security.CustomUserDetailsService;
import com.sigmav.sigmavbackend.security.JwtTokenProvider;
import com.sigmav.sigmavbackend.security.SecurityConfig;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for POST /api/admin/profile/change-password.
 *
 * The endpoint is self-service: it always operates on the authenticated
 * principal's own id, so it must be reachable by every authenticated role, not
 * only by ACADEMY_ADMIN.
 *
 * Note: {@code @WebMvcTest} alone does not pick up {@code SecurityConfig}, so
 * the slice would fall back to Spring Boot's default security auto-configuration
 * (CSRF on, method security off) and {@code @PreAuthorize} would never be
 * evaluated. Importing {@code SecurityConfig} is what makes these assertions
 * meaningful. Without it the slice does NOT fail loudly: every test here still
 * passes, including with the production annotation removed, because nothing
 * ever evaluates the guard. Removing the import silently turns this class into
 * the vacuous shape that {@code AdminDashboardControllerFeaturesTest} already has.
 */
@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminDashboardController — POST /api/admin/profile/change-password")
class AdminDashboardControllerChangePasswordTest {

    private static final String ENDPOINT = "/api/admin/profile/change-password";
    private static final String BODY =
            "{\"currentPassword\":\"old-secret\",\"newPassword\":\"new-secret\"}";

    @Autowired MockMvc mockMvc;

    // Controller dependencies
    @MockitoBean AcademyService academyService;
    @MockitoBean AdminDashboardService adminDashboardService;
    @MockitoBean StorageService storageService;
    @MockitoBean PublicBrandingService publicBrandingService;

    // Security filter chain dependencies (JwtAuthenticationFilter transitive)
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean CustomUserDetailsService customUserDetailsService;
    @MockitoBean StringRedisTemplate stringRedisTemplate;

    // ─── helper ───────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken principalFor(Long userId, Long academyId, Role role) {
        UserPrincipal principal = new UserPrincipal(
                userId, academyId, "user@test.com", "hashed", role.name(), true, null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    // ─── Test 1: ACADEMIC_COORDINATOR → 200 ───────────────────────────────────

    @Test
    @DisplayName("authenticated ACADEMIC_COORDINATOR → 200 (self-service, not role-locked)")
    void changePassword_academicCoordinator_returns200() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(authentication(principalFor(42L, 2L, Role.ACADEMIC_COORDINATOR))))
                .andExpect(status().isOk());
    }

    // ─── Test 2: SALES_ADVISOR → 200 ──────────────────────────────────────────

    @Test
    @DisplayName("authenticated SALES_ADVISOR → 200")
    void changePassword_salesAdvisor_returns200() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(authentication(principalFor(43L, 2L, Role.SALES_ADVISOR))))
                .andExpect(status().isOk());
    }

    // ─── Test 3: COLLECTIONS_MANAGER → 200 ────────────────────────────────────

    @Test
    @DisplayName("authenticated COLLECTIONS_MANAGER → 200")
    void changePassword_collectionsManager_returns200() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(authentication(principalFor(44L, 2L, Role.COLLECTIONS_MANAGER))))
                .andExpect(status().isOk());
    }

    // ─── Test 4: ACADEMY_ADMIN still works ────────────────────────────────────

    @Test
    @DisplayName("authenticated ACADEMY_ADMIN → 200")
    void changePassword_academyAdmin_returns200() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(authentication(principalFor(1L, 1L, Role.ACADEMY_ADMIN))))
                .andExpect(status().isOk());
    }

    // ─── Test 5: unauthenticated → rejected, service untouched ────────────────

    @Test
    @DisplayName("unauthenticated request is rejected and never reaches the service")
    void changePassword_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminDashboardService);
    }

    // ─── Test 6: operates on the principal's own id ───────────────────────────

    @Test
    @DisplayName("service is called with the principal's own id — no id is accepted from the body")
    void changePassword_usesIdFromPrincipal() throws Exception {
        Long principalId = 42L;

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(csrf())
                        .with(authentication(principalFor(principalId, 2L, Role.ACADEMIC_COORDINATOR))))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<ChangePasswordRequest> requestCaptor =
                ArgumentCaptor.forClass(ChangePasswordRequest.class);
        verify(adminDashboardService).changePassword(idCaptor.capture(), requestCaptor.capture());

        assertThat(idCaptor.getValue())
                .as("Service must be called with the principal's own id (42), never a foreign id")
                .isEqualTo(principalId);
        assertThat(requestCaptor.getValue().currentPassword()).isEqualTo("old-secret");
        assertThat(requestCaptor.getValue().newPassword()).isEqualTo("new-secret");
    }
}

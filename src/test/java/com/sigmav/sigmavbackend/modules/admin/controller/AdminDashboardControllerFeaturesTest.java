package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.modules.admin.service.AdminDashboardService;
import com.sigmav.sigmavbackend.modules.public_.service.PublicBrandingService;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.security.CustomUserDetailsService;
import com.sigmav.sigmavbackend.security.JwtTokenProvider;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import com.sigmav.sigmavbackend.modules.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for GET /api/admin/academy/features.
 *
 * JwtAuthenticationFilter, JwtTokenProvider, CustomUserDetailsService, and
 * StringRedisTemplate are mocked so the JWT security filter chain can be
 * constructed without hitting real infrastructure.
 */
@WebMvcTest(AdminDashboardController.class)
@DisplayName("AdminDashboardController — GET /api/admin/academy/features")
class AdminDashboardControllerFeaturesTest {

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

    private static UsernamePasswordAuthenticationToken principalFor(Long academyId, Role role) {
        UserPrincipal principal = new UserPrincipal(
                42L, academyId, "user@test.com", "hashed", role.name(), true, null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    // ─── Test 1: ACADEMY_ADMIN → 200 with feature map ─────────────────────────

    @Test
    @DisplayName("authenticated ACADEMY_ADMIN → 200 with feature map")
    void getFeatures_academyAdmin_returns200WithFeatureMap() throws Exception {
        Long academyId = 1L;
        when(academyService.getFeatures(academyId)).thenReturn(
                Map.of("exams", true, "engagementScore", false));

        mockMvc.perform(get("/api/admin/academy/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(authentication(principalFor(academyId, Role.ACADEMY_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exams").value(true))
                .andExpect(jsonPath("$.engagementScore").value(false));
    }

    // ─── Test 2: ACADEMIC_COORDINATOR → 200 ───────────────────────────────────

    @Test
    @DisplayName("authenticated ACADEMIC_COORDINATOR → 200 (endpoint is isAuthenticated, not role-locked)")
    void getFeatures_academicCoordinator_returns200() throws Exception {
        Long academyId = 2L;
        when(academyService.getFeatures(academyId)).thenReturn(Map.of("exams", true));

        mockMvc.perform(get("/api/admin/academy/features")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(authentication(principalFor(academyId, Role.ACADEMIC_COORDINATOR))))
                .andExpect(status().isOk());
    }

    // ─── Test 3: unauthenticated → 401 ────────────────────────────────────────

    @Test
    @DisplayName("unauthenticated request → 401")
    void getFeatures_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/academy/features"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Test 4: cross-academy isolation ──────────────────────────────────────

    @Test
    @DisplayName("service is called with principal's own academyId — cross-academy isolation")
    void getFeatures_usesAcademyIdFromPrincipal() throws Exception {
        Long principalAcademyId = 1L;
        when(academyService.getFeatures(principalAcademyId)).thenReturn(Map.of());

        mockMvc.perform(get("/api/admin/academy/features")
                        .with(authentication(principalFor(principalAcademyId, Role.ACADEMY_ADMIN))))
                .andExpect(status().isOk());

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(academyService).getFeatures(captor.capture());
        assertThat(captor.getValue())
                .as("Service must be called with the principal's academyId (1), not any foreign id")
                .isEqualTo(principalAcademyId);
    }
}

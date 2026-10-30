package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization slice test for {@link AdminDashboardController}.
 *
 * The controller used to carry a class-level {@code @PreAuthorize("hasRole('ACADEMY_ADMIN')")}.
 * {@code hasRole} matches the literal role string, so a holder of a custom role could never
 * pass it no matter which permissions that role had been granted — the granted permission was
 * decorative for every endpoint guarded only by that class-level rule. The class-level rule is
 * now replaced by an explicit per-method guard so that permission holders reach the endpoints
 * their permissions actually cover, while GET /api/admin/dashboard deliberately stays
 * role-locked to ACADEMY_ADMIN.
 *
 * Note: {@code @WebMvcTest} alone does NOT pick up {@code SecurityConfig}, so the slice would
 * fall back to Spring Boot's default security auto-configuration (CSRF on, method security off)
 * and {@code @PreAuthorize} would never be evaluated — every test below would pass even with
 * the production annotations removed. Importing {@code SecurityConfig} is what makes these
 * assertions meaningful. The 403 assertions are themselves the proof that the guard ran: the
 * filter chain only requires an authenticated request, so an authenticated caller reaching a
 * 403 can only have been stopped by method security.
 */
@WebMvcTest(AdminDashboardController.class)
@Import(SecurityConfig.class)
@DisplayName("AdminDashboardController — per-method permission authorization")
class AdminDashboardControllerPermissionAuthorizationTest {

    private static final String BRANDING = "/api/admin/branding";
    private static final String LOGO_UPLOAD = "/api/admin/branding/logo-upload";
    private static final String REPORT_SCHEDULE = "/api/admin/report-schedule";
    private static final String CALENDAR = "/api/admin/calendar";
    private static final String DASHBOARD = "/api/admin/dashboard";
    private static final String MY_ACADEMY = "/api/admin/my-academy";
    private static final String FEATURES = "/api/admin/academy/features";
    private static final String CHANGE_PASSWORD = "/api/admin/profile/change-password";

    /** Not a built-in role: a custom role an academy admin created. */
    private static final String CUSTOM_ROLE = "BRANDING_MANAGER";

    private static final String BRANDING_BODY =
            "{\"logoUrl\":\"https://cdn.test/logo.png\",\"primaryColor\":\"#123456\",\"features\":[\"a\"]}";
    private static final String SCHEDULE_BODY =
            "{\"reportScheduleDay\":\"MONDAY\",\"reportScheduleHour\":8}";
    private static final String PASSWORD_BODY =
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

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** A holder of a custom (non built-in) role carrying exactly the given permissions. */
    private static UsernamePasswordAuthenticationToken customRoleWith(String... permissions) {
        return principalFor(CUSTOM_ROLE, Set.of(permissions));
    }

    /**
     * V27__add_permissions_tables.sql:55-56 seeds 'ACADEMY_ADMIN' against every row of
     * `permissions`, so the built-in academy admin really does hold these authorities — the
     * principal below mirrors production rather than asserting a wish.
     */
    private static UsernamePasswordAuthenticationToken academyAdmin() {
        return principalFor(Role.ACADEMY_ADMIN.name(),
                Set.of("MANAGE_ACADEMY_CONFIG", "MANAGE_SCHEDULE", "MANAGE_ROLES", "REGISTER_STUDENT"));
    }

    private static UsernamePasswordAuthenticationToken principalFor(String role, Set<String> permissions) {
        UserPrincipal principal = new UserPrincipal(
                50L, 2L, "user@test.com", "hashed", role, true, permissions);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    // ─── 1. the regression: permission holder, custom role, reaches branding ──

    @Test
    @DisplayName("custom role holding MANAGE_ACADEMY_CONFIG → 200 on PUT /admin/branding (was 403)")
    void updateBranding_customRoleWithPermission_returns200() throws Exception {
        mockMvc.perform(put(BRANDING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BRANDING_BODY)
                        .with(csrf())
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("custom role holding MANAGE_ACADEMY_CONFIG → 200 on POST /admin/branding/logo-upload")
    void uploadLogo_customRoleWithPermission_returns200() throws Exception {
        when(storageService.uploadLogo(anyString(), any(byte[].class), anyString()))
                .thenReturn("https://cdn.test/logo.png");

        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(LOGO_UPLOAD)
                        .file(file)
                        .with(csrf())
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("custom role holding MANAGE_ACADEMY_CONFIG → 200 on PUT /admin/report-schedule")
    void updateReportSchedule_customRoleWithPermission_returns200() throws Exception {
        mockMvc.perform(put(REPORT_SCHEDULE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHEDULE_BODY)
                        .with(csrf())
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isOk());
    }

    // ─── 2. the deliberate exclusion still holds ──────────────────────────────

    @Test
    @DisplayName("custom role holding MANAGE_ACADEMY_CONFIG → 403 on GET /admin/dashboard (role-locked by design)")
    void getDashboard_customRoleWithPermission_returns403() throws Exception {
        mockMvc.perform(get(DASHBOARD)
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isForbidden());
    }

    // ─── 3. missing permission is still refused ───────────────────────────────

    @Test
    @DisplayName("custom role without MANAGE_ACADEMY_CONFIG → 403 on PUT /admin/branding")
    void updateBranding_withoutPermission_returns403() throws Exception {
        mockMvc.perform(put(BRANDING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BRANDING_BODY)
                        .with(csrf())
                        .with(authentication(customRoleWith("MANAGE_SCHEDULE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("custom role without MANAGE_ACADEMY_CONFIG → 403 on PUT /admin/report-schedule")
    void updateReportSchedule_withoutPermission_returns403() throws Exception {
        mockMvc.perform(put(REPORT_SCHEDULE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHEDULE_BODY)
                        .with(csrf())
                        .with(authentication(customRoleWith("MANAGE_SCHEDULE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("custom role without MANAGE_SCHEDULE → 403 on GET /admin/calendar")
    void getCalendar_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get(CALENDAR)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isForbidden());
    }

    // ─── 4. the calendar gate is MANAGE_SCHEDULE, not MANAGE_ACADEMY_CONFIG ───

    @Test
    @DisplayName("custom role holding MANAGE_SCHEDULE → 200 on GET /admin/calendar")
    void getCalendar_customRoleWithSchedulePermission_returns200() throws Exception {
        mockMvc.perform(get(CALENDAR)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(authentication(customRoleWith("MANAGE_SCHEDULE"))))
                .andExpect(status().isOk());
    }

    // ─── 5. the endpoints left at isAuthenticated() stay open ─────────────────

    @Test
    @DisplayName("custom role with no permissions → 200 on GET /admin/my-academy (non-admin flow reads plan limits)")
    void getAcademyLimits_customRoleWithoutPermissions_returns200() throws Exception {
        // Decided by the frontend: the cycles screen is guarded by
        // ['MANAGE_SCHEDULE', 'ASSIGN_TEACHERS'] yet calls getAcademyLimits() unconditionally,
        // so a non-admin permission holder really does hit this endpoint today.
        mockMvc.perform(get(MY_ACADEMY).with(authentication(customRoleWith())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("custom role with no permissions → 200 on GET /admin/academy/features")
    void getMyAcademyFeatures_customRoleWithoutPermissions_returns200() throws Exception {
        mockMvc.perform(get(FEATURES).with(authentication(customRoleWith())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("custom role with no permissions → 200 on POST /admin/profile/change-password")
    void changePassword_customRoleWithoutPermissions_returns200() throws Exception {
        mockMvc.perform(post(CHANGE_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_BODY)
                        .with(csrf())
                        .with(authentication(customRoleWith())))
                .andExpect(status().isOk());
    }

    // ─── 6. no regression for the built-in role ───────────────────────────────

    @Test
    @DisplayName("ACADEMY_ADMIN still reaches every endpoint it reached before")
    void academyAdmin_reachesEveryEndpoint() throws Exception {
        when(storageService.uploadLogo(anyString(), any(byte[].class), anyString()))
                .thenReturn("https://cdn.test/logo.png");

        mockMvc.perform(get(DASHBOARD).with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get(MY_ACADEMY).with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get(FEATURES).with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(put(BRANDING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BRANDING_BODY)
                        .with(csrf())
                        .with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(LOGO_UPLOAD)
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1}))
                        .with(csrf())
                        .with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(put(REPORT_SCHEDULE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SCHEDULE_BODY)
                        .with(csrf())
                        .with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(get(CALENDAR)
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(authentication(academyAdmin())))
                .andExpect(status().isOk());
        mockMvc.perform(post(CHANGE_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_BODY)
                        .with(csrf())
                        .with(authentication(academyAdmin())))
                .andExpect(status().isOk());
    }

    // ─── 7. unauthenticated is rejected everywhere ────────────────────────────
    //
    // The exact status is deliberately not pinned: SecurityConfig declares no
    // AuthenticationEntryPoint, so an anonymous request is refused with 403 rather than 401.
    // Asserting is4xxClientError() follows AdminDashboardControllerChangePasswordTest and
    // keeps this test about "the guard rejects anonymous callers" rather than about which
    // of the two rejection shapes the entry point happens to produce.

    @Test
    @DisplayName("unauthenticated request is rejected on PUT /admin/branding and never reaches the service")
    void updateBranding_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(put(BRANDING)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BRANDING_BODY)
                        .with(csrf()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminDashboardService, publicBrandingService);
    }

    @Test
    @DisplayName("unauthenticated request is rejected on GET /admin/dashboard and never reaches the service")
    void getDashboard_unauthenticated_isRejected() throws Exception {
        mockMvc.perform(get(DASHBOARD))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(adminDashboardService);
    }
}

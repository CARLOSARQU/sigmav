package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.modules.admin.dto.EngagementScoreResponse;
import com.sigmav.sigmavbackend.modules.admin.service.EngagementScoreService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization slice test for {@link EngagementScoreController}.
 *
 * The controller used to carry a class-level {@code @PreAuthorize("hasRole('ACADEMY_ADMIN')")}.
 * {@code hasRole} matches the literal role string, so a holder of a custom role could never
 * pass it no matter which permissions that role had been granted — the granted permission was
 * decorative. The class-level rule is now replaced by a method-level guard on the single
 * {@code getScores()} endpoint, gated on the {@code VIEW_REPORTS} authority, so that custom
 * roles reach engagement analytics while the endpoint stops being role-locked.
 *
 * Note: {@code @WebMvcTest} alone does NOT pick up {@code SecurityConfig}, so the slice would
 * fall back to Spring Boot's default security auto-configuration (CSRF on, method security off)
 * and {@code @PreAuthorize} would never be evaluated — every test below would pass even with
 * the production annotations removed. Importing {@code SecurityConfig} is what makes these
 * assertions meaningful. The 403 assertions are themselves the proof that the guard ran: the
 * filter chain only requires an authenticated request, so an authenticated caller reaching a
 * 403 can only have been stopped by method security.
 *
 * {@code @RequiresFeature(Feature.ENGAGEMENT_SCORE)} also sits on {@code getScores()}, but its
 * {@code FeatureCheckAspect} is an {@code @Aspect @Component} and therefore excluded from this
 * slice by {@code WebMvcTypeExcludeFilter} — which is why {@code AcademyService} (the aspect's
 * only dependency) is deliberately not declared as a {@code @MockitoBean} here: had the aspect
 * been loaded, the context would have failed to start instead of quietly passing. The feature
 * gate is covered by its own slice; these tests are about the permission gate.
 */
@WebMvcTest(EngagementScoreController.class)
@Import(SecurityConfig.class)
@DisplayName("EngagementScoreController — per-method permission authorization")
class EngagementScoreControllerPermissionAuthorizationTest {

    private static final String SCORES = "/api/admin/engagement/scores";

    /** Not a built-in role: a custom role an academy admin created. */
    private static final String CUSTOM_ROLE = "ANALYTICS_VIEWER";

    @Autowired MockMvc mockMvc;

    // Controller dependency
    @MockitoBean EngagementScoreService engagementScoreService;

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
     * The built-in academy admin role, carrying exactly the given permissions.
     *
     * V27__add_permissions_tables.sql:55-56 seeds 'ACADEMY_ADMIN' against every row of
     * `permissions`, so in production this role does hold VIEW_REPORTS — but the principal is
     * built explicitly rather than derived, so the case below can pin the role/permission
     * separation the guard is supposed to enforce.
     */
    private static UsernamePasswordAuthenticationToken academyAdminWith(String... permissions) {
        return principalFor(Role.ACADEMY_ADMIN.name(), Set.of(permissions));
    }

    private static UsernamePasswordAuthenticationToken principalFor(String role, Set<String> permissions) {
        UserPrincipal principal = new UserPrincipal(
                50L, 2L, "user@test.com", "hashed", role, true, permissions);
        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    private static EngagementScoreResponse emptyScores() {
        return new EngagementScoreResponse(List.of(), 0.0);
    }

    // ─── 1. the regression: the permission holder, custom role, reaches scores ─

    @Test
    @DisplayName("custom role holding VIEW_REPORTS → 200 on GET /admin/engagement/scores (was 403)")
    void getScores_customRoleWithViewReports_returns200() throws Exception {
        when(engagementScoreService.getEngagementScores(any())).thenReturn(emptyScores());

        mockMvc.perform(get(SCORES)
                        .with(authentication(customRoleWith("VIEW_REPORTS"))))
                .andExpect(status().isOk());
    }

    // ─── 2. missing permission is still refused ───────────────────────────────

    @Test
    @DisplayName("custom role without VIEW_REPORTS → 403 on GET /admin/engagement/scores")
    void getScores_withoutViewReports_returns403() throws Exception {
        mockMvc.perform(get(SCORES)
                        .with(authentication(customRoleWith("MANAGE_ACADEMY_CONFIG"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(engagementScoreService);
    }

    // ─── 3. the point of the change: the guard is no longer role-based ────────
    //
    // This is the case the old class-level `hasRole('ACADEMY_ADMIN')` could not express.
    // Before the change it returned 200 (the role matched, the permission was never read);
    // after the change it returns 403 because the authority, not the role, decides access.

    @Test
    @DisplayName("ACADEMY_ADMIN without VIEW_REPORTS → 403 on GET /admin/engagement/scores (role alone no longer grants)")
    void getScores_academyAdminWithoutViewReports_returns403() throws Exception {
        mockMvc.perform(get(SCORES)
                        .with(authentication(academyAdminWith("MANAGE_ACADEMY_CONFIG", "MANAGE_ROLES"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(engagementScoreService);
    }

    // ─── 4. no regression for the built-in role that actually holds it ────────

    @Test
    @DisplayName("ACADEMY_ADMIN holding VIEW_REPORTS → 200 on GET /admin/engagement/scores")
    void getScores_academyAdminWithViewReports_returns200() throws Exception {
        when(engagementScoreService.getEngagementScores(any())).thenReturn(emptyScores());

        mockMvc.perform(get(SCORES)
                        .with(authentication(academyAdminWith("VIEW_REPORTS"))))
                .andExpect(status().isOk());
    }

    // ─── 5. unauthenticated is rejected and never reaches the service ─────────
    //
    // The exact status is 403 rather than 401: SecurityConfig declares no
    // AuthenticationEntryPoint, so authorization failures fall through to the default
    // Http403ForbiddenEntryPoint. Pinned exactly (unlike the sibling dashboard test, which
    // only asserts 4xx) because the contract for this endpoint names 403 specifically.

    @Test
    @DisplayName("unauthenticated request → 403 on GET /admin/engagement/scores and never reaches the service")
    void getScores_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get(SCORES))
                .andExpect(status().isForbidden());

        verifyNoInteractions(engagementScoreService);
    }
}

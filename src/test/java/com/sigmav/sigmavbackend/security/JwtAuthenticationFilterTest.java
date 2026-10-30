package com.sigmav.sigmavbackend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the JWT authentication filter.
 *
 * A valid signature alone is not enough: the loaded principal must still be an
 * enabled account, otherwise a deactivated staff member would keep working with
 * an already-issued token until it expires.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — token validation and account status")
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final Long USER_ID = 7L;
    private static final Long ACADEMY_ID = 1L;

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private StringRedisTemplate stringRedisTemplate;

    @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("disabled principal → 401 ACCOUNT_DISABLED, no SecurityContext, filter chain not invoked")
    void disabledPrincipal_returns401_andDoesNotContinueChain() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getRoleFromToken(TOKEN)).thenReturn("TEACHER");
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal(false, "TEACHER"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ACCOUNT_DISABLED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("enabled principal → authentication set in SecurityContext and filter chain continues")
    void enabledPrincipal_setsAuthentication_andContinuesChain() throws Exception {
        MockHttpServletRequest request = requestWithBearerToken();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getRoleFromToken(TOKEN)).thenReturn("ACADEMY_ADMIN");
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(userDetailsService.loadUserById(USER_ID)).thenReturn(principal(true, "ACADEMY_ADMIN"));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOf(UserPrincipal.class);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest requestWithBearerToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private UserPrincipal principal(boolean active, String role) {
        return new UserPrincipal(
                USER_ID, ACADEMY_ID, "staff@test.com", "hashed", role, active, Set.of());
    }
}

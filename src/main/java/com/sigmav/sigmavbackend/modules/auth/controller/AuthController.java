package com.sigmav.sigmavbackend.modules.auth.controller;

import com.sigmav.sigmavbackend.modules.auth.dto.ForgotPasswordRequest;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginRequest;
import com.sigmav.sigmavbackend.modules.auth.dto.LoginResponse;
import com.sigmav.sigmavbackend.modules.auth.dto.ResetPasswordRequest;
import com.sigmav.sigmavbackend.modules.auth.service.AuthService;
import com.sigmav.sigmavbackend.modules.permission.service.PermissionService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PermissionService permissionService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyPermissions(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<String> permissions = List.copyOf(
                permissionService.getEffectivePermissions(principal.getId(), principal.getAcademyId()));
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email(), request.subdomain());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}

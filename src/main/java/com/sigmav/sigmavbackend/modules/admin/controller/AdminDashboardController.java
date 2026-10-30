package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.modules.admin.dto.AcademyLimitsResponse;
import com.sigmav.sigmavbackend.modules.admin.dto.AdminDashboardResponse;
import com.sigmav.sigmavbackend.modules.admin.dto.BrandingUpdateRequest;
import com.sigmav.sigmavbackend.modules.admin.dto.ChangePasswordRequest;
import com.sigmav.sigmavbackend.modules.admin.dto.ReportScheduleRequest;
import com.sigmav.sigmavbackend.modules.admin.service.AdminDashboardService;
import com.sigmav.sigmavbackend.modules.public_.service.PublicBrandingService;
import com.sigmav.sigmavbackend.modules.storage.service.StorageService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


/**
 * Every method carries its own explicit guard. There is deliberately no class-level
 * {@code @PreAuthorize} here: a class-level {@code hasRole('ACADEMY_ADMIN')} compares the
 * literal role string, so a holder of a custom role could never satisfy it no matter which
 * permissions that role had been granted, which made those grants decorative.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;
    private final StorageService storageService;
    private final PublicBrandingService publicBrandingService;
    private final AcademyService academyService;

    @GetMapping("/academy/features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> getMyAcademyFeatures(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(academyService.getFeatures(principal.getAcademyId()));
    }

    /**
     * B8.7 — Academy admin dashboard.
     *
     * Deliberately still role-locked to ACADEMY_ADMIN rather than permission-based: this is the
     * academy owner's cross-domain overview (enrollments + finances + attendance), and every
     * other built-in role already has its own dashboard (/teacher/dashboard,
     * /student/dashboard, /superadmin/dashboard). Custom roles are excluded on purpose, and no
     * permission of their own should open it — the frontend now derives the post-login landing
     * route from the user's own navigation entries, so a custom-role user is never sent here.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    /**
     * Current plan limits and usage — used by the frontend for blocks and counters.
     *
     * Stays at isAuthenticated(): the cycles screen is guarded by
     * ['MANAGE_SCHEDULE', 'ASSIGN_TEACHERS'] (frontend/src/app/app.routes.ts:101) yet calls
     * getAcademyLimits() unconditionally
     * (frontend/src/app/modules/admin/cycles/cycles.component.ts:66), so non-admin permission
     * holders hit this endpoint today. Reading your own academy's plan limits is not a
     * privileged act.
     */
    @GetMapping("/my-academy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AcademyLimitsResponse> getAcademyLimits() {
        return ResponseEntity.ok(dashboardService.getAcademyLimits());
    }

    /** POST /api/admin/profile/change-password — cada usuario autenticado cambia su propia contraseña */
    @PostMapping("/profile/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        dashboardService.changePassword(principal.getId(), request);
        return ResponseEntity.ok().build();
    }

    /** PUT /api/admin/branding — actualiza logo, color primario y features de login */
    @PutMapping("/branding")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> updateBranding(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BrandingUpdateRequest request) {
        dashboardService.updateBranding(principal.getAcademyId(), request);
        publicBrandingService.evictBrandingCache();
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/branding/logo-upload (multipart/form-data)
     * Recibe el archivo desde el frontend, lo sube a R2 y devuelve la URL pública permanente.
     */
    @PostMapping(value = "/branding/logo-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Map<String, String>> uploadLogo(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file) throws java.io.IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
        String ext = contentType.contains("jpeg") ? "jpg" : contentType.split("/")[1];
        String key = "logos/" + principal.getAcademyId() + "/" + java.util.UUID.randomUUID() + "." + ext;
        String publicUrl = storageService.uploadLogo(key, file.getBytes(), contentType);
        return ResponseEntity.ok(Map.of("publicUrl", publicUrl));
    }

    /**
     * PUT /api/admin/report-schedule — configura día y hora de reportes para apoderados.
     *
     * Same permission as branding: it writes academy-level delivery configuration.
     */
    @PutMapping("/report-schedule")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> updateReportSchedule(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReportScheduleRequest request) {
        dashboardService.updateReportSchedule(principal.getAcademyId(),
                request.reportScheduleDay(), request.reportScheduleHour());
        return ResponseEntity.ok().build();
    }

    /** GET /api/admin/calendar — sesiones activas de la academia en un rango de fechas */
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<List<Map<String, Object>>> getCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardService.getCalendarEvents(from, to));
    }
}

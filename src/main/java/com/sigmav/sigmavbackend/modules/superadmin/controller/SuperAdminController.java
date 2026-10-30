package com.sigmav.sigmavbackend.modules.superadmin.controller;

import com.sigmav.sigmavbackend.modules.academy.dto.*;
import com.sigmav.sigmavbackend.modules.academy.service.AcademyService;
import com.sigmav.sigmavbackend.modules.superadmin.dto.*;
import com.sigmav.sigmavbackend.modules.superadmin.service.SuperAdminService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final AcademyService academyService;
    private final SuperAdminService superAdminService;

    // --- Dashboard ---

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(superAdminService.getDashboard());
    }

    // --- Academies CRUD ---

    @GetMapping("/academies")
    public ResponseEntity<Page<AcademyResponse>> listAcademies(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String planType,
            Pageable pageable) {
        return ResponseEntity.ok(academyService.listAll(isActive, planType, pageable));
    }

    @GetMapping("/academies/{id}")
    public ResponseEntity<AcademyResponse> getAcademy(@PathVariable Long id) {
        return ResponseEntity.ok(academyService.getById(id));
    }

    @PostMapping("/academies")
    public ResponseEntity<AcademyResponse> createAcademy(@Valid @RequestBody CreateAcademyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(academyService.create(request));
    }

    @PatchMapping("/academies/{id}")
    public ResponseEntity<AcademyResponse> updateAcademy(@PathVariable Long id,
                                                          @RequestBody UpdateAcademyRequest request) {
        return ResponseEntity.ok(academyService.update(id, request));
    }

    // --- Features ---

    @PatchMapping("/academies/{id}/features")
    public ResponseEntity<AcademyResponse> updateFeatures(@PathVariable Long id,
                                                           @RequestBody UpdateFeaturesRequest request) {
        return ResponseEntity.ok(academyService.updateFeatures(id, request));
    }

    // --- Activate/Deactivate ---

    @PatchMapping("/academies/{id}/activate")
    public ResponseEntity<AcademyResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(academyService.activate(id));
    }

    @PatchMapping("/academies/{id}/deactivate")
    public ResponseEntity<AcademyResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(academyService.deactivate(id));
    }

    // --- Subscriptions ---

    @PostMapping("/academies/{id}/subscriptions")
    public ResponseEntity<SubscriptionResponse> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody CreateSubscriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(academyService.registerPayment(id, request, principal.getId()));
    }

    @GetMapping("/academies/{id}/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> getPaymentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(academyService.getPaymentHistory(id));
    }

    // --- Usuarios por academia ---

    @GetMapping("/academies/{id}/users")
    public ResponseEntity<Page<UserSummaryResponse>> listAcademyUsers(
            @PathVariable Long id,
            Pageable pageable) {
        return ResponseEntity.ok(superAdminService.listAcademyUsers(id, pageable));
    }

    @PostMapping("/academies/{id}/users")
    public ResponseEntity<UserSummaryResponse> createAcademyUser(
            @PathVariable Long id,
            @Valid @RequestBody CreateAcademyUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(superAdminService.createAcademyUser(id, request));
    }

    // --- Gestión global de usuarios ---

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) Long academyId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(superAdminService.listUsers(academyId, role, isActive, q, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserSummaryResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(superAdminService.getUserById(id));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<UserSummaryResponse> updateUser(@PathVariable Long id,
                                                           @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(superAdminService.updateUser(id, request));
    }

    @PatchMapping("/users/{id}/password")
    public ResponseEntity<Void> changeUserPassword(@PathVariable Long id,
                                                    @Valid @RequestBody ChangePasswordRequest request) {
        superAdminService.changeUserPassword(id, request);
        return ResponseEntity.ok().build();
    }
}

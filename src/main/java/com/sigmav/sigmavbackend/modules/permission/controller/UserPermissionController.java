package com.sigmav.sigmavbackend.modules.permission.controller;

import com.sigmav.sigmavbackend.modules.permission.dto.AdminUserSummary;
import com.sigmav.sigmavbackend.modules.permission.dto.EffectivePermissionsResponse;
import com.sigmav.sigmavbackend.modules.permission.dto.PermissionOverrideRequest;
import com.sigmav.sigmavbackend.modules.permission.dto.RoleUpdateRequest;
import com.sigmav.sigmavbackend.modules.permission.service.PermissionService;
import com.sigmav.sigmavbackend.modules.student.dto.DniLookupResponse;
import com.sigmav.sigmavbackend.modules.student.service.DniLookupService;
import com.sigmav.sigmavbackend.modules.user.dto.CreateStaffUserRequest;
import com.sigmav.sigmavbackend.modules.user.dto.StaffUserCreatedResponse;
import com.sigmav.sigmavbackend.modules.user.dto.UpdateUserCredentialsRequest;
import com.sigmav.sigmavbackend.modules.user.entity.CustomRole;
import com.sigmav.sigmavbackend.modules.user.entity.Role;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.CustomRoleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.modules.user.service.UserService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserPermissionController {

    private final PermissionService      permissionService;
    private final UserService            userService;
    private final UserRepository         userRepository;
    private final CustomRoleRepository   customRoleRepository;
    private final DniLookupService       dniLookupService;

    /**
     * Returns all active staff users in the caller's academy, excluding STUDENT.
     * Used by the admin UI to populate the user management table.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES') or hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<List<AdminUserSummary>> listAcademyUsers(
            @AuthenticationPrincipal UserPrincipal principal) {

        Long academyId = principal.getAcademyId();
        List<AdminUserSummary> users = userRepository.findByAcademyIdAndIsActiveTrue(academyId)
                .stream()
                .filter(u -> !"STUDENT".equals(u.getRole()))
                .map(u -> new AdminUserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.getIsActive()))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<StaffUserCreatedResponse> createStaffUser(
            @Valid @RequestBody CreateStaffUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        StaffUserCreatedResponse response = userService.createStaffUser(request, principal.getAcademyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/lookup-dni")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<DniLookupResponse> lookupDni(@RequestParam String dni) {
        return ResponseEntity.ok(dniLookupService.lookup(dni));
    }

    /**
     * Returns the effective permissions and override list for a target user.
     * Enforces academy boundary: caller and target must belong to the same academy.
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<EffectivePermissionsResponse> getPermissions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateSameAcademy(id, principal);
        return ResponseEntity.ok(permissionService.getUserPermissions(id, principal.getAcademyId()));
    }

    /**
     * Updates the role of a target user. Triggers @CacheEvict on user-principals
     * so the changed role (and re-resolved permissions) take effect immediately.
     * Validates that the role is either a built-in role or an active custom role
     * belonging to the caller's academy.
     */
    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateSameAcademy(id, principal);

        String role = request.role();
        if (!Role.isBuiltIn(role)) {
            CustomRole customRole = customRoleRepository.findByNameAndAcademyId(role, principal.getAcademyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Role '" + role + "' is not a built-in role and no active custom role with that name exists in this academy"));
            if (!customRole.getIsActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role '" + role + "' is currently inactive and cannot be assigned");
            }
        }

        userService.updateRole(id, role, principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the email and/or password of a target user.
     * Only ACADEMY_ADMIN (PERMISSION_MANAGE_ACADEMY_CONFIG) can call this.
     * Email uniqueness is validated globally before the change is applied.
     */
    @PutMapping("/{id}/credentials")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> updateCredentials(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserCredentialsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateSameAcademy(id, principal);
        userService.updateCredentials(id, request.email(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds or modifies a per-user permission override. Triggers @CacheEvict via
     * PermissionService.saveOverride so changes take effect on the next request.
     */
    @PostMapping("/{id}/permissions/override")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<Void> addOverride(
            @PathVariable Long id,
            @Valid @RequestBody PermissionOverrideRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        validateSameAcademy(id, principal);
        permissionService.saveOverride(
                id,
                request.permissionName(),
                principal.getAcademyId(),
                request.granted());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns all assignable roles for the caller's academy.
     * Combines built-in role names with active custom role names.
     * Used by the admin UI to populate the role dropdown when editing users.
     */
    @GetMapping("/available-roles")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<List<String>> getAvailableRoles(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<String> roles = new ArrayList<>(Role.getBuiltInRoles());
        customRoleRepository.findByAcademyIdAndIsActiveTrue(principal.getAcademyId())
                .forEach(cr -> roles.add(cr.getName()));
        return ResponseEntity.ok(roles);
    }

    /**
     * Loads the target user and verifies they belong to the same academy as the
     * caller. Throws HTTP 403 if the academies differ (spec B6.4 / security requirement).
     */
    private void validateSameAcademy(Long targetUserId, UserPrincipal principal) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + targetUserId));

        if (principal.getAcademyId() != null
                && !principal.getAcademyId().equals(target.getAcademyId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cannot manage permissions of a user in another academy");
        }
    }
}

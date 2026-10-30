package com.sigmav.sigmavbackend.modules.permission.controller;

import com.sigmav.sigmavbackend.modules.permission.repository.PermissionRepository;
import com.sigmav.sigmavbackend.modules.user.dto.CreateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.dto.CustomRoleResponse;
import com.sigmav.sigmavbackend.modules.user.dto.UpdateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.service.CustomRoleService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class CustomRoleController {

    private final CustomRoleService customRoleService;
    private final PermissionRepository permissionRepository;

    /**
     * Lists all active custom roles for the caller's academy with their permissions.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<List<CustomRoleResponse>> listRoles(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(customRoleService.list(principal.getAcademyId()));
    }

    /**
     * Creates a new custom role with the specified permissions for the caller's academy.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<CustomRoleResponse> createRole(
            @Valid @RequestBody CreateCustomRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CustomRoleResponse response = customRoleService.create(principal.getAcademyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing custom role: name, description, active status, and/or permissions.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<CustomRoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CustomRoleResponse response = customRoleService.update(id, principal.getAcademyId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deletes a custom role. Throws 409 if users are currently assigned this role.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        customRoleService.delete(id, principal.getAcademyId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns all available permission names from the permissions catalogue.
     * Used by the admin UI to populate permission checkboxes.
     */
    @GetMapping("/available-permissions")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ROLES')")
    public ResponseEntity<List<String>> getAvailablePermissions() {
        return ResponseEntity.ok(permissionRepository.findAllPermissionNames());
    }
}

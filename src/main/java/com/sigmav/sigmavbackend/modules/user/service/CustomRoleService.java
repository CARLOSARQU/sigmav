package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.permission.repository.PermissionRepository;
import com.sigmav.sigmavbackend.modules.user.dto.CreateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.dto.CustomRoleResponse;
import com.sigmav.sigmavbackend.modules.user.dto.UpdateCustomRoleRequest;
import com.sigmav.sigmavbackend.modules.user.entity.CustomRole;
import com.sigmav.sigmavbackend.modules.user.entity.Role;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.CustomRoleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomRoleService {

    private final CustomRoleRepository customRoleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    /**
     * Creates a new custom role for the given academy with the specified permissions.
     * Validates: the academy is present, name not blank, not a built-in role name,
     * unique within the academy.
     */
    @Transactional
    public CustomRoleResponse create(Long academyId, CreateCustomRoleRequest request) {
        // The academy is the role's identity, not an attribute: permission resolution joins
        // users to custom_roles on the (name, academy_id) pair, so a role without an academy
        // can never match a user and its grants could never reach anyone. Refused here — where
        // every caller inherits it — instead of letting a row exist that nothing can use.
        if (academyId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot create a custom role without an academy: a custom role is always owned by exactly one academy");
        }
        String name = request.name();
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name must not be blank");
        }
        if (Role.isBuiltIn(name.toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot create a custom role with a built-in role name: " + name);
        }
        if (customRoleRepository.existsByNameAndAcademyId(name, academyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A custom role with this name already exists in the academy");
        }

        CustomRole role = CustomRole.builder()
                .academyId(academyId)
                .name(name)
                .description(request.description())
                .isActive(true)
                .build();
        role = customRoleRepository.save(role);

        if (request.permissions() != null) {
            for (String perm : request.permissions()) {
                permissionRepository.insertCustomRolePermission(role.getId(), perm);
            }
        }

        return toResponse(role, request.permissions() != null ? request.permissions() : List.of());
    }

    /**
     * Updates an existing custom role. If the name changes, validates that it is not
     * a built-in role name, is unique within the academy, and no users currently have
     * the old role assigned. If permissions are provided, replaces the role's grant set.
     *
     * A rename alone never touches the grants: they are keyed on the role's immutable id,
     * so the stored set follows the role instead of being rewritten under a new name.
     */
    @Transactional
    public CustomRoleResponse update(Long id, Long academyId, UpdateCustomRoleRequest request) {
        CustomRole role = customRoleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Custom role not found: " + id));

        if (!role.getAcademyId().equals(academyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Custom role does not belong to this academy");
        }

        String oldName = role.getName();

        String newName = request.name();
        if (newName != null && !newName.isBlank() && !newName.equals(oldName)) {
            if (Role.isBuiltIn(newName.toUpperCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot rename to a built-in role name: " + newName);
            }
            if (customRoleRepository.existsByNameAndAcademyId(newName, academyId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A custom role with this name already exists in the academy");
            }
            long userCount = userRepository.countByRoleAndAcademyId(oldName, academyId);
            if (userCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot rename role because " + userCount + " user(s) currently have this role assigned");
            }
            role.setName(newName);
        }

        if (request.description() != null) {
            role.setDescription(request.description());
        }
        // Measured against the stored state, not merely against the request carrying the
        // field: re-sending the current value changes nothing and must not evict.
        boolean isActiveChanged = request.isActive() != null
                && !request.isActive().equals(role.getIsActive());
        if (request.isActive() != null) {
            role.setIsActive(request.isActive());
        }

        role = customRoleRepository.save(role);
        String effectiveName = role.getName();

        List<String> permissions;
        boolean rolePermissionsChanged = false;
        if (request.permissions() != null) {
            // Replace the whole set. Grants are keyed on the stable role id, so a rename
            // in the same request needs no special handling: the id does not move.
            permissionRepository.deleteCustomRolePermissions(role.getId());
            for (String perm : request.permissions()) {
                permissionRepository.insertCustomRolePermission(role.getId(), perm);
            }
            permissions = request.permissions();
            rolePermissionsChanged = true;
        } else {
            // A rename alone changes no grants: the rows follow the id, so the stored
            // set is still exactly the role's own.
            permissions = permissionRepository.findPermissionsByCustomRoleId(role.getId());
        }

        if (rolePermissionsChanged || isActiveChanged) {
            // The cached UserPrincipal carries the effective permissions, so without this a
            // holder keeps the old set for up to 5 minutes (PermissionService already evicts
            // for per-user overrides; this is the role-level path that was missed).
            // Deactivation belongs in this gate even though no permission row moves: it
            // takes the role's whole set away from its holders, who stay assigned until
            // they are reassigned.
            evictPrincipalsForRole(effectiveName, academyId);
        }

        return toResponse(role, permissions);
    }

    /**
     * Soft-deletes a custom role by setting isActive=false.
     * Throws 409 Conflict if any users currently have this role assigned.
     */
    @Transactional
    public void delete(Long id, Long academyId) {
        CustomRole role = customRoleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Custom role not found: " + id));

        if (!role.getAcademyId().equals(academyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Custom role does not belong to this academy");
        }

        long userCount = userRepository.countByRoleAndAcademyId(role.getName(), academyId);
        if (userCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete role because " + userCount + " user(s) currently have this role assigned");
        }

        role.setIsActive(false);
        customRoleRepository.save(role);
    }

    /**
     * Returns all active custom roles for the academy with their permissions.
     */
    public List<CustomRoleResponse> list(Long academyId) {
        return customRoleRepository.findByAcademyIdAndIsActiveTrue(academyId)
                .stream()
                .map(r -> toResponse(r, permissionRepository.findPermissionsByCustomRoleId(r.getId())))
                .toList();
    }

    /**
     * Returns a single custom role by id + academyId with its permissions.
     */
    public CustomRoleResponse getById(Long id, Long academyId) {
        CustomRole role = customRoleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Custom role not found: " + id));

        if (!role.getAcademyId().equals(academyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Custom role does not belong to this academy");
        }

        List<String> permissions = permissionRepository.findPermissionsByCustomRoleId(role.getId());
        return toResponse(role, permissions);
    }

    /**
     * Evicts the cached principal of every user in the academy holding the given role name.
     *
     * Scoped to the academy because the grants are. Custom-role permissions are keyed on
     * custom_roles.id and resolved through the (name, academy_id) pair
     * (PermissionRepository.findEffectivePermissionsForUser), so an update to one academy's
     * role changes the effective permissions of that academy's holders and nobody else's.
     * The former unscoped reach existed only because role names were a global namespace
     * shared across academies; with that gone it would evict unrelated tenants' entries.
     *
     * Uses the manual CacheManager form rather than @CacheEvict on purpose: the annotation
     * only fires through the Spring proxy, so a future internal self-invocation would
     * silently stop evicting.
     */
    private void evictPrincipalsForRole(String roleName, Long academyId) {
        for (User user : userRepository.findByRoleAndAcademyIdOrderByFullName(roleName, academyId)) {
            cacheManager.getCache("user-principals").evict(user.getId());
        }
    }

    private CustomRoleResponse toResponse(CustomRole role, List<String> permissions) {
        return new CustomRoleResponse(
                role.getId(),
                role.getUuid(),
                role.getAcademyId(),
                role.getName(),
                role.getDescription(),
                role.getIsActive(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                permissions
        );
    }
}

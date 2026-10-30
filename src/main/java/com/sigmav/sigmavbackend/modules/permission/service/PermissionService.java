package com.sigmav.sigmavbackend.modules.permission.service;

import com.sigmav.sigmavbackend.modules.permission.dto.EffectivePermissionsResponse;
import com.sigmav.sigmavbackend.modules.permission.dto.OverrideItem;
import com.sigmav.sigmavbackend.modules.permission.entity.UserPermissionOverride;
import com.sigmav.sigmavbackend.modules.permission.entity.UserPermissionOverrideId;
import com.sigmav.sigmavbackend.modules.permission.repository.PermissionRepository;
import com.sigmav.sigmavbackend.modules.permission.repository.UserPermissionOverrideRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionService {

    static final Set<String> ALL_PERMISSIONS = Set.of(
            "REGISTER_STUDENT",
            "VIEW_ENROLLMENTS",
            "MANAGE_ENROLLMENTS",
            "READ_FINANCES",
            "WRITE_FINANCES",
            "MANAGE_SCHEDULE",
            "ASSIGN_TEACHERS",
            "MANAGE_EXAMS",
            "VIEW_ATTENDANCE",
            "MANAGE_ATTENDANCE",
            "EDIT_GRADES",
            "VIEW_REPORTS",
            "MANAGE_STUDENTS",
            "MANAGE_ACADEMY_CONFIG",
            "MANAGE_ROLES"
    );

    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final UserRepository userRepository;

    /**
     * Returns the effective permission set for a user in their academy context.
     * SUPER_ADMIN bypass: returns all 15 permissions without any DB query on the
     * permissions tables (design constraint C4).
     */
    public Set<String> getEffectivePermissions(Long userId, Long academyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        if ("SUPER_ADMIN".equals(user.getRole())) {
            return ALL_PERMISSIONS;
        }

        if (academyId == null) {
            return Set.of();
        }

        List<String> resolved = permissionRepository.findEffectivePermissionsForUser(userId, academyId);
        return new HashSet<>(resolved);
    }

    /**
     * Returns the effective permissions and override list for a user.
     * Enforces academy-boundary: if the target user belongs to a different
     * academy than requestingUserAcademyId, throws 403.
     */
    public EffectivePermissionsResponse getUserPermissions(Long userId, Long requestingUserAcademyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        if (requestingUserAcademyId != null
                && !requestingUserAcademyId.equals(user.getAcademyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot access permissions of a user in another academy");
        }

        Set<String> effective = getEffectivePermissions(userId, user.getAcademyId());

        List<UserPermissionOverride> rawOverrides =
                overrideRepository.findByIdUserIdAndAcademyId(userId, user.getAcademyId());

        List<OverrideItem> overrides = rawOverrides.stream()
                .map(o -> new OverrideItem(o.getId().getPermissionName(), o.isGranted()))
                .toList();

        return new EffectivePermissionsResponse(effective, overrides);
    }

    /**
     * Upserts a permission override for a user+permission pair and evicts the
     * user-principals cache so the change takes effect on the next request.
     */
    @Transactional
    @CacheEvict(value = "user-principals", key = "#userId")
    public void saveOverride(Long userId, String permissionName, Long academyId, boolean granted) {
        UserPermissionOverrideId id = new UserPermissionOverrideId(userId, permissionName);

        UserPermissionOverride override = overrideRepository.findById(id)
                .orElse(UserPermissionOverride.builder().id(id).build());

        override.setAcademyId(academyId);
        override.setGranted(granted);
        overrideRepository.save(override);
    }
}

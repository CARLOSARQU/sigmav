package com.sigmav.sigmavbackend.modules.user.service;

import com.sigmav.sigmavbackend.modules.user.dto.CreateStaffUserRequest;
import com.sigmav.sigmavbackend.modules.user.dto.StaffUserCreatedResponse;
import com.sigmav.sigmavbackend.modules.user.entity.CustomRole;
import com.sigmav.sigmavbackend.modules.user.entity.Role;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.CustomRoleRepository;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomRoleRepository customRoleRepository;

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) return trimmed;
        if (trimmed.matches("^9\\d{8}$")) return "+51" + trimmed;
        return trimmed;
    }

    /**
     * Updates the role of the given user and immediately evicts the
     * user-principals Redis cache entry so the next request re-resolves
     * the effective permissions for the new role.
     *
     * <p>Privilege boundaries are enforced here — not only in the controller — so
     * that no other call path can bypass them: an administrator can never change
     * their own role, and only a SUPER_ADMIN can grant the SUPER_ADMIN role.
     *
     * @param userId  the ID of the user whose role is being changed
     * @param newRole the new role to assign
     * @param caller  the authenticated principal performing the change
     * @throws ResponseStatusException HTTP 403 if the change is not permitted,
     *                                 HTTP 404 if the user does not exist
     */
    @Transactional
    @CacheEvict(value = "user-principals", key = "#userId")
    public void updateRole(Long userId, String newRole, UserPrincipal caller) {
        Long callerId = caller != null ? caller.getId() : null;
        if (callerId != null && callerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "An administrator cannot change their own role");
        }
        if ("SUPER_ADMIN".equals(newRole)
                && (caller == null || !"SUPER_ADMIN".equals(caller.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a SUPER_ADMIN can assign the SUPER_ADMIN role");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));
        user.setRole(newRole);
        userRepository.save(user);
    }

    @Transactional
    public StaffUserCreatedResponse createStaffUser(CreateStaffUserRequest req, Long academyId) {
        // Validate role: must be either built-in staff role or an active custom role in the academy
        boolean isBuiltIn = Role.isBuiltIn(req.role());
        boolean isStudentOrSuperAdmin = "STUDENT".equals(req.role()) || "SUPER_ADMIN".equals(req.role());
        if (isStudentOrSuperAdmin) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role not allowed for staff creation");
        }
        if (!isBuiltIn) {
            CustomRole customRole = customRoleRepository.findByNameAndAcademyId(req.role(), academyId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Custom role not found: " + req.role()));
            // A deactivated role grants nothing: PermissionRepository resolves custom-role
            // permissions only while custom_roles.is_active is true, so accepting it here would
            // create a user who resolves zero permissions with no error anywhere. Mirrors the
            // guard in UserPermissionController.updateRole, so both assignment paths refuse the
            // same input with the same message instead of disagreeing about it.
            if (!customRole.getIsActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Role '" + req.role() + "' is currently inactive and cannot be assigned");
            }
        }
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        String rawPassword = (req.password() != null && !req.password().isBlank())
                ? req.password() : generatePassword(8);
        String generatedPassword = (req.password() != null && !req.password().isBlank())
                ? null : rawPassword;
        User user = User.builder()
                .academyId(academyId)
                .email(email)
                .fullName(req.fullName())
                .role(req.role())
                .passwordHash(passwordEncoder.encode(rawPassword))
                .isActive(true)
                .build();
        User saved = userRepository.save(user);
        String phone = (req.phone() != null && !req.phone().isBlank())
                ? normalizePhone(req.phone()) : null;
        return new StaffUserCreatedResponse(saved.getId(), saved.getEmail(), saved.getFullName(), saved.getRole(), generatedPassword, phone);
    }

    /**
     * Updates the email and/or password of a user. Only ACADEMY_ADMIN can call this.
     * Email is validated for global uniqueness (across all academies) via
     * {@link UserRepository#existsByEmailAndIdNot}. Password is optional — if left
     * blank the existing password is preserved.
     */
    @Transactional
    public void updateCredentials(Long userId, String email, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + userId));

        if (email != null && !email.isBlank()) {
            String normalized = email.toLowerCase().trim();
            if (userRepository.existsByEmailAndIdNot(normalized, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(normalized);
        }

        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        userRepository.save(user);
    }

    private String generatePassword(int length) {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }
}

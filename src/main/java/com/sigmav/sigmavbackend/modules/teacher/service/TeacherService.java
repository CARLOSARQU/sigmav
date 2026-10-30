package com.sigmav.sigmavbackend.modules.teacher.service;

import java.security.SecureRandom;
import com.sigmav.sigmavbackend.modules.teacher.dto.CreateTeacherRequest;
import com.sigmav.sigmavbackend.modules.teacher.dto.TeacherResponse;
import com.sigmav.sigmavbackend.modules.teacher.dto.UpdateTeacherRequest;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    public List<TeacherResponse> listAll() {
        return userRepository.findByRoleAndAcademyIdOrderByFullName("TEACHER", TenantContext.getCurrentAcademyId())
                .stream().map(this::toResponse).toList();
    }

    public TeacherResponse getById(Long id) {
        return toResponse(findOrFail(id));
    }

    @Transactional
    public TeacherResponse create(CreateTeacherRequest request) {
        Long academyId = TenantContext.getCurrentAcademyId();

        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe un usuario con el email: " + email);
        }

        String rawPassword = (request.password() != null && !request.password().isBlank())
                ? request.password() : null;
        if (rawPassword != null && rawPassword.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }
        String generatedPassword = null;
        if (rawPassword == null) {
            rawPassword = generatePassword();
            generatedPassword = rawPassword;
        }

        User teacher = User.builder()
                .academyId(academyId)
                .fullName(request.fullName())
                .dni(request.dni())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("TEACHER")
                .build();

        return toResponse(userRepository.save(teacher), generatedPassword);
    }

    @Transactional
    public TeacherResponse update(Long id, UpdateTeacherRequest request) {
        User teacher = findOrFail(id);

        if (request.fullName() != null) teacher.setFullName(request.fullName());
        if (request.dni() != null) {
            Long academyId = TenantContext.getCurrentAcademyId();
            if (!request.dni().equals(teacher.getDni())
                    && userRepository.existsByDniAndAcademyIdAndRoleAndIdNot(request.dni(), academyId, "TEACHER", teacher.getId())) {
                throw new IllegalArgumentException("Ya existe un docente con el DNI: " + request.dni());
            }
            teacher.setDni(request.dni());
        }
        if (request.email() != null) {
            String email = request.email().toLowerCase().trim();
            if (!teacher.getEmail().equals(email)
                    && userRepository.existsByEmailAndIdNot(email, teacher.getId())) {
                throw new IllegalArgumentException("El email ya está en uso: " + email);
            }
            teacher.setEmail(email);
        }
        if (request.password() != null && !request.password().isBlank()) {
            teacher.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return toResponse(userRepository.save(teacher));
    }

    @Transactional
    public void deactivate(Long id) {
        User teacher = findOrFail(id);
        teacher.setIsActive(false);
        userRepository.save(teacher);

        // The cached UserPrincipal carries isActive, so without this evict a
        // deactivated teacher keeps full access until the 5-minute TTL expires.
        //
        // Residual race: this statement runs inside the @Transactional boundary,
        // and the commit happens after the method returns. A concurrent read can
        // therefore still reload the pre-commit row and repopulate the entry
        // before the commit lands. The window collapses from the full TTL to a
        // sub-millisecond race, but it is not airtight; closing it would need a
        // post-commit eviction hook.
        cacheManager.getCache("user-principals").evict(teacher.getId());
    }

    public User findOrFail(Long id) {
        return userRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .filter(u -> "TEACHER".equals(u.getRole()))
                .orElseThrow(() -> new NotFoundException("Docente no encontrado con ID: " + id));
    }

    private TeacherResponse toResponse(User u) {
        return toResponse(u, null);
    }

    private TeacherResponse toResponse(User u, String generatedPassword) {
        return new TeacherResponse(u.getId(), u.getUuid(), u.getFullName(),
                u.getDni(), u.getEmail(), u.getAvatarUrl(), u.getIsActive(), u.getCreatedAt(),
                generatedPassword);
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }
}

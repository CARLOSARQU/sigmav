package com.sigmav.sigmavbackend.modules.student.service;

import com.sigmav.sigmavbackend.modules.enrollment.repository.EnrollmentRepository;
import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import com.sigmav.sigmavbackend.modules.guardian.dto.GuardianResponse;
import com.sigmav.sigmavbackend.modules.guardian.service.GuardianService;
import com.sigmav.sigmavbackend.modules.student.dto.*;
import com.sigmav.sigmavbackend.modules.student.entity.Student;
import com.sigmav.sigmavbackend.modules.student.repository.StudentRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.security.SecureRandom;
import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EnrollmentRepository enrollmentRepository;
    private final CacheManager cacheManager;
    private final GuardianService guardianService;

    public Page<StudentResponse> listAll(String search, Boolean isActive, Pageable pageable) {
        Long academyId = TenantContext.getCurrentAcademyId();
        Page<Student> page;
        if (search != null && !search.isBlank()) {
            page = studentRepository.searchStudentsWithFilter(academyId, search, isActive, pageable);
        } else {
            page = studentRepository.searchStudentsNoFilter(academyId, isActive, pageable);
        }

        List<Long> ids = page.getContent().stream().map(Student::getId).toList();
        Set<Long> withEnrollment = ids.isEmpty()
                ? Collections.emptySet()
                : enrollmentRepository.findActiveEnrollmentStudentIds(ids);

        return page.map(s -> toResponse(s, withEnrollment.contains(s.getId()), null));
    }

    public StudentResponse getById(Long id) {
        return toResponse(findOrFail(id));
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        Long academyId = TenantContext.getCurrentAcademyId();

        if (studentRepository.existsByDniAndAcademyId(request.dni(), academyId)) {
            throw new IllegalArgumentException("Ya existe un estudiante con el DNI: " + request.dni());
        }
        // Generar código automático si no se proporcionó uno
        String code = request.code();
        if (code == null || code.isBlank()) {
            int year = Year.now().getValue();
            long seq = studentRepository.countByAcademyId(academyId) + 1;
            String candidate = year + "-" + String.format("%04d", seq);
            // Garantizar unicidad en caso de eliminaciones previas
            while (studentRepository.existsByCodeAndAcademyId(candidate, academyId)) {
                seq++;
                candidate = year + "-" + String.format("%04d", seq);
            }
            code = candidate;
        } else if (studentRepository.existsByCodeAndAcademyId(code, academyId)) {
            throw new IllegalArgumentException("Ya existe un estudiante con el código: " + code);
        }
        String email = request.email() != null
                ? request.email().toLowerCase().trim()
                : request.dni() + "@" + academyId + ".local";
        if (request.email() != null && userRepository.existsByEmail(email)) {
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

        User user = User.builder()
                .academyId(academyId)
                .fullName(request.firstName() + " " + request.lastName())
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role("STUDENT")
                .build();
        user = userRepository.save(user);

        Student student = Student.builder()
                .academyId(academyId)
                .userId(user.getId())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .dni(request.dni())
                .email(request.email() != null ? email : null)
                .phone(normalizePhone(request.phone()))
                .code(code)
                .build();

        Student saved = studentRepository.save(student);

        // Guardar apoderados si se enviaron
        if (request.guardians() != null && !request.guardians().isEmpty()) {
            guardianService.createAll(saved.getId(), academyId, request.guardians());
        }

        return toResponse(saved, generatedPassword);
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        // Tenant-scoped lookup: a student belonging to another academy must not be
        // updatable. findOrFail returns 404 (not 403) so the API does not leak that
        // the row exists elsewhere.
        Student student = findOrFail(id);

        if (request.firstName() != null) student.setFirstName(request.firstName());
        if (request.lastName() != null) student.setLastName(request.lastName());
        if (request.dni() != null) {
            Long academyId = TenantContext.getCurrentAcademyId();
            if (!request.dni().equals(student.getDni())
                    && studentRepository.existsByDniAndAcademyIdAndIdNot(request.dni(), academyId, student.getId())) {
                throw new IllegalArgumentException("Ya existe un estudiante con el DNI: " + request.dni());
            }
            student.setDni(request.dni());
        }
        String newEmail = request.email() != null ? request.email().toLowerCase().trim() : null;
        if (newEmail != null) {
            Long excludeUserId = student.getUserId();
            boolean conflict = excludeUserId != null
                    ? userRepository.existsByEmailAndIdNot(newEmail, excludeUserId)
                    : userRepository.existsByEmail(newEmail);
            if (conflict) throw new IllegalArgumentException("El email ya está en uso: " + newEmail);
            student.setEmail(newEmail);
        }
        if (request.phone() != null) student.setPhone(normalizePhone(request.phone()));
        if (request.code() != null) student.setCode(request.code());

        // Sincronizar fullName y email en el User asociado (el login usa User.email)
        if (student.getUserId() != null && (request.firstName() != null || request.lastName() != null || newEmail != null)) {
            userRepository.findById(student.getUserId()).ifPresent(u -> {
                if (request.firstName() != null || request.lastName() != null) {
                    u.setFullName(student.getFirstName() + " " + student.getLastName());
                }
                if (newEmail != null) {
                    u.setEmail(newEmail);
                }
                userRepository.save(u);
            });
        }

        Student saved = studentRepository.save(student);

        // Apoderados: null = no tocar, vacío = limpiar, poblado = reemplazar
        if (request.guardians() != null) {
            if (request.guardians().isEmpty()) {
                guardianService.deleteByStudentId(saved.getId(), saved.getAcademyId());
            } else {
                guardianService.replaceAll(saved.getId(), saved.getAcademyId(), request.guardians());
            }
        }

        return toResponse(saved);
    }

    /**
     * Reemplaza los apoderados de un estudiante de forma independiente.
     * Lista vacía = eliminar todos; poblada = reemplazar; null no debería ocurrir vía controller.
     */
    @Transactional
    public void updateGuardians(Long studentId, List<CreateGuardianRequest> guardians, Long academyId) {
        Student student = findOrFail(studentId);
        if (guardians == null || guardians.isEmpty()) {
            guardianService.deleteByStudentId(student.getId(), academyId);
        } else {
            guardianService.replaceAll(student.getId(), academyId, guardians);
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Student student = findOrFail(id);
        student.setIsActive(false);
        studentRepository.save(student);

        // Desactivar también el User asociado para bloquear el login
        if (student.getUserId() != null) {
            userRepository.findById(student.getUserId()).ifPresent(u -> {
                u.setIsActive(false);
                userRepository.save(u);
                cacheManager.getCache("user-principals").evict(u.getId());
            });
        }
    }

    @Transactional
    public void activate(Long id) {
        Student student = findOrFail(id);
        student.setIsActive(true);
        studentRepository.save(student);

        if (student.getUserId() != null) {
            userRepository.findById(student.getUserId()).ifPresent(u -> {
                u.setIsActive(true);
                userRepository.save(u);
                cacheManager.getCache("user-principals").evict(u.getId());
            });
        }
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        Student student = findOrFail(id);
        if (student.getUserId() == null) {
            throw new IllegalArgumentException("Este estudiante no tiene cuenta de acceso");
        }
        User user = userRepository.findById(student.getUserId())
                .orElseThrow(() -> new NotFoundException("Usuario del estudiante no encontrado"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        cacheManager.getCache("user-principals").evict(user.getId());
    }

    public Student findOrFail(Long id) {
        return studentRepository.findByIdAndAcademyId(id, TenantContext.getCurrentAcademyId())
                .orElseThrow(() -> new NotFoundException("Estudiante no encontrado con ID: " + id));
    }

    private StudentResponse toResponse(Student s) {
        return toResponse(s, enrollmentRepository.existsActiveEnrollmentForStudent(s.getId()), null);
    }

    private StudentResponse toResponse(Student s, String generatedPassword) {
        return toResponse(s, enrollmentRepository.existsActiveEnrollmentForStudent(s.getId()), generatedPassword);
    }

    private StudentResponse toResponse(Student s, boolean hasActiveEnrollment, String generatedPassword) {
        List<GuardianResponse> guardians = guardianService.findByStudentId(s.getId(), s.getAcademyId());
        return new StudentResponse(s.getId(), s.getUuid(), s.getAcademyId(), s.getUserId(),
                s.getFirstName(), s.getLastName(), s.getFullName(),
                s.getDni(), s.getEmail(), s.getPhone(), s.getCode(),
                s.getAvatarUrl(), s.getIsActive(), s.getCreatedAt(), hasActiveEnrollment,
                generatedPassword, guardians);
    }

    private String generatePassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * Normaliza un número de teléfono peruano.
     * - null/blank → null
     * - ya tiene prefijo internacional (+) → se deja igual
     * - 9 dígitos empezando con 9 → se antepone +51
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) return trimmed;
        if (trimmed.matches("^9\\d{8}$")) return "+51" + trimmed;
        return trimmed;
    }
}

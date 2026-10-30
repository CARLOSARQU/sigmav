package com.sigmav.sigmavbackend.modules.guardian.service;

import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import com.sigmav.sigmavbackend.modules.guardian.dto.GuardianResponse;
import com.sigmav.sigmavbackend.modules.guardian.entity.Guardian;
import com.sigmav.sigmavbackend.modules.guardian.repository.GuardianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final GuardianRepository guardianRepository;

    public List<GuardianResponse> findByStudentId(Long studentId, Long academyId) {
        return guardianRepository.findByStudentIdAndIsActiveTrue(studentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<GuardianResponse> createAll(Long studentId, Long academyId, List<CreateGuardianRequest> requests) {
        List<Guardian> guardians = requests.stream()
                .map(req -> Guardian.builder()
                        .academyId(academyId)
                        .studentId(studentId)
                        .fullName(req.fullName())
                        .phone(normalizePhone(req.phone()))
                        .email(normalizeEmail(req.email()))
                        .relationship(req.relationship())
                        .sendReports(req.sendReports() != null ? req.sendReports() : true)
                        .build())
                .toList();
        return guardianRepository.saveAll(guardians)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<GuardianResponse> replaceAll(Long studentId, Long academyId, List<CreateGuardianRequest> requests) {
        guardianRepository.deleteByStudentIdAndAcademyId(studentId, academyId);
        return createAll(studentId, academyId, requests);
    }

    public void deleteByStudentId(Long studentId, Long academyId) {
        guardianRepository.deleteByStudentIdAndAcademyId(studentId, academyId);
    }

    private GuardianResponse toResponse(Guardian g) {
        return new GuardianResponse(
                g.getId(),
                g.getUuid(),
                g.getStudentId(),
                g.getFullName(),
                g.getPhone(),
                g.getEmail(),
                g.getRelationship(),
                g.getIsActive(),
                g.getCreatedAt(),
                g.getSendReports()
        );
    }

    /**
     * Normaliza número peruano: 9 dígitos → +51XXXXXXXXX, internacional → se deja igual.
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) return trimmed;
        if (trimmed.matches("^9\\d{8}$")) return "+51" + trimmed;
        return trimmed;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.toLowerCase().trim();
    }
}

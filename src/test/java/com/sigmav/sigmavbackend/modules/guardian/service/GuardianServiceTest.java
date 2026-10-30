package com.sigmav.sigmavbackend.modules.guardian.service;

import com.sigmav.sigmavbackend.modules.guardian.dto.CreateGuardianRequest;
import com.sigmav.sigmavbackend.modules.guardian.dto.GuardianResponse;
import com.sigmav.sigmavbackend.modules.guardian.entity.Guardian;
import com.sigmav.sigmavbackend.modules.guardian.entity.RelationshipType;
import com.sigmav.sigmavbackend.modules.guardian.repository.GuardianRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardianService — Gestión de apoderados")
class GuardianServiceTest {

    private static final Long ACADEMY_ID = 1L;
    private static final Long STUDENT_ID = 100L;

    @Mock
    private GuardianRepository guardianRepository;

    @InjectMocks
    private GuardianService guardianService;

    @Captor
    private ArgumentCaptor<List<Guardian>> guardianListCaptor;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentAcademyId(ACADEMY_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Guardian buildGuardian(Long id, String fullName, RelationshipType relationship) {
        return Guardian.builder()
                .id(id)
                .uuid(UUID.randomUUID())
                .academyId(ACADEMY_ID)
                .studentId(STUDENT_ID)
                .fullName(fullName)
                .phone("999888777")
                .email(fullName.toLowerCase().replace(" ", ".") + "@test.com")
                .relationship(relationship)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CreateGuardianRequest buildRequest(String fullName, RelationshipType relationship) {
        return new CreateGuardianRequest(
                fullName,
                "999888777",
                fullName.toLowerCase().replace(" ", ".") + "@test.com",
                relationship,
                true
        );
    }

    // ── findByStudentId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByStudentId: returns mapped responses for active guardians")
    void findByStudentId_returnsMappedResponses() {
        Guardian guardian1 = buildGuardian(1L, "María García", RelationshipType.MADRE);
        Guardian guardian2 = buildGuardian(2L, "Juan García", RelationshipType.PADRE);
        when(guardianRepository.findByStudentIdAndIsActiveTrue(STUDENT_ID))
                .thenReturn(List.of(guardian1, guardian2));

        List<GuardianResponse> result = guardianService.findByStudentId(STUDENT_ID, ACADEMY_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GuardianResponse::fullName)
                .containsExactly("María García", "Juan García");
        verify(guardianRepository).findByStudentIdAndIsActiveTrue(STUDENT_ID);
    }

    @Test
    @DisplayName("findByStudentId: empty list when no guardians")
    void findByStudentId_emptyWhenNoGuardians() {
        when(guardianRepository.findByStudentIdAndIsActiveTrue(STUDENT_ID))
                .thenReturn(List.of());

        List<GuardianResponse> result = guardianService.findByStudentId(STUDENT_ID, ACADEMY_ID);

        assertThat(result).isEmpty();
    }

    // ── createAll ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAll: saves guardians and returns responses")
    void createAll_savesAndReturnsResponses() {
        CreateGuardianRequest req1 = buildRequest("María García", RelationshipType.MADRE);
        CreateGuardianRequest req2 = buildRequest("Juan García", RelationshipType.PADRE);
        List<CreateGuardianRequest> requests = List.of(req1, req2);

        Guardian saved1 = buildGuardian(1L, "María García", RelationshipType.MADRE);
        Guardian saved2 = buildGuardian(2L, "Juan García", RelationshipType.PADRE);
        when(guardianRepository.saveAll(any())).thenReturn(List.of(saved1, saved2));

        List<GuardianResponse> result = guardianService.createAll(STUDENT_ID, ACADEMY_ID, requests);

        assertThat(result).hasSize(2);
        verify(guardianRepository).saveAll(guardianListCaptor.capture());
        List<Guardian> captured = guardianListCaptor.getValue();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(captured.get(0).getAcademyId()).isEqualTo(ACADEMY_ID);
        assertThat(captured.get(0).getRelationship()).isEqualTo(RelationshipType.MADRE);
    }

    @Test
    @DisplayName("createAll: empty request list returns empty response")
    void createAll_emptyRequest_returnsEmpty() {
        when(guardianRepository.saveAll(any())).thenReturn(List.of());

        List<GuardianResponse> result = guardianService.createAll(STUDENT_ID, ACADEMY_ID, List.of());

        assertThat(result).isEmpty();
        verify(guardianRepository).saveAll(any());
    }

    // ── replaceAll ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("replaceAll: deletes existing and saves new guardians")
    void replaceAll_deletesAndSaves() {
        CreateGuardianRequest req = buildRequest("Nuevo Tutor", RelationshipType.TUTOR);
        Guardian saved = buildGuardian(3L, "Nuevo Tutor", RelationshipType.TUTOR);
        when(guardianRepository.saveAll(any())).thenReturn(List.of(saved));

        List<GuardianResponse> result = guardianService.replaceAll(STUDENT_ID, ACADEMY_ID, List.of(req));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Nuevo Tutor");
        verify(guardianRepository).deleteByStudentIdAndAcademyId(STUDENT_ID, ACADEMY_ID);
        verify(guardianRepository).saveAll(any());
    }

    @Test
    @DisplayName("replaceAll: empty list clears guardians")
    void replaceAll_emptyList_clearsGuardians() {
        when(guardianRepository.saveAll(any())).thenReturn(List.of());

        List<GuardianResponse> result = guardianService.replaceAll(STUDENT_ID, ACADEMY_ID, List.of());

        assertThat(result).isEmpty();
        verify(guardianRepository).deleteByStudentIdAndAcademyId(STUDENT_ID, ACADEMY_ID);
        verify(guardianRepository).saveAll(any());
    }

    // ── deleteByStudentId ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteByStudentId: delegates to repository")
    void deleteByStudentId_delegatesToRepository() {
        guardianService.deleteByStudentId(STUDENT_ID, ACADEMY_ID);

        verify(guardianRepository).deleteByStudentIdAndAcademyId(STUDENT_ID, ACADEMY_ID);
    }
}

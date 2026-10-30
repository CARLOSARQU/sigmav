package com.sigmav.sigmavbackend.modules.simulacro.service;

import com.sigmav.sigmavbackend.modules.exam.entity.Exam;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAreaMapping;
import com.sigmav.sigmavbackend.modules.exam.entity.ExamAreaMappingId;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamAreaMappingRepository;
import com.sigmav.sigmavbackend.modules.exam.repository.ExamRepository;
import com.sigmav.sigmavbackend.modules.simulacro.dto.*;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroArea;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroCareer;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroUniversity;
import com.sigmav.sigmavbackend.modules.simulacro.repository.SimulacroAreaRepository;
import com.sigmav.sigmavbackend.modules.simulacro.repository.SimulacroCareerRepository;
import com.sigmav.sigmavbackend.modules.simulacro.repository.SimulacroUniversityRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulacroService — Gestión de universidades, áreas, carreras y vinculación por área")
class SimulacroServiceTest {

    private static final Long ACADEMY_ID = 1L;
    private static final Long UNIVERSITY_ID = 10L;
    private static final Long AREA_ID = 20L;
    private static final Long CAREER_ID = 30L;
    private static final Long EXAM_ID = 40L;

    @Mock
    private SimulacroUniversityRepository universityRepository;

    @Mock
    private SimulacroAreaRepository areaRepository;

    @Mock
    private SimulacroCareerRepository careerRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ExamAreaMappingRepository examAreaMappingRepository;

    @InjectMocks
    private SimulacroService simulacroService;

    @Captor
    private ArgumentCaptor<SimulacroUniversity> universityCaptor;

    @Captor
    private ArgumentCaptor<SimulacroArea> areaCaptor;

    @Captor
    private ArgumentCaptor<SimulacroCareer> careerCaptor;

    @Captor
    private ArgumentCaptor<ExamAreaMapping> mappingCaptor;

    // =========================================================================
    // getCareers() — student-facing career listing
    // =========================================================================

    @Test
    @DisplayName("getCareers: no universities → empty list")
    void getCareers_noUniversities_returnsEmpty() {
        when(universityRepository.findByAcademyIdAndIsActiveTrueOrderByName(ACADEMY_ID))
                .thenReturn(List.of());

        List<CareerResponse> result = simulacroService.getCareers(ACADEMY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCareers: universities present but no areas → empty list")
    void getCareers_noAreas_returnsEmpty() {
        SimulacroUniversity uni = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("Universidad Nacional").build();
        when(universityRepository.findByAcademyIdAndIsActiveTrueOrderByName(ACADEMY_ID))
                .thenReturn(List.of(uni));

        List<Long> uniIds = List.of(UNIVERSITY_ID);
        when(areaRepository.findByUniversityIdInAndIsActiveTrue(uniIds))
                .thenReturn(List.of());

        List<CareerResponse> result = simulacroService.getCareers(ACADEMY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCareers: universities and areas present but no careers → empty list")
    void getCareers_noCareers_returnsEmpty() {
        SimulacroUniversity uni = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("Universidad Nacional").build();
        SimulacroArea area = SimulacroArea.builder()
                .id(AREA_ID).academyId(ACADEMY_ID).universityId(UNIVERSITY_ID).name("Ciencias").build();

        when(universityRepository.findByAcademyIdAndIsActiveTrueOrderByName(ACADEMY_ID))
                .thenReturn(List.of(uni));
        when(areaRepository.findByUniversityIdInAndIsActiveTrue(List.of(UNIVERSITY_ID)))
                .thenReturn(List.of(area));

        List<Long> areaIds = List.of(AREA_ID);
        when(careerRepository.findByAreaIdInAndIsActiveTrue(areaIds))
                .thenReturn(List.of());

        List<CareerResponse> result = simulacroService.getCareers(ACADEMY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCareers: full hierarchy → returns enriched CareerResponse list")
    void getCareers_fullHierarchy_returnsEnrichedCareers() {
        SimulacroUniversity uni = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("Universidad Nacional").build();
        SimulacroArea area = SimulacroArea.builder()
                .id(AREA_ID).academyId(ACADEMY_ID).universityId(UNIVERSITY_ID).name("Ciencias de la Salud").build();
        SimulacroCareer career = SimulacroCareer.builder()
                .id(CAREER_ID).academyId(ACADEMY_ID).areaId(AREA_ID).name("Medicina Humana").build();

        when(universityRepository.findByAcademyIdAndIsActiveTrueOrderByName(ACADEMY_ID))
                .thenReturn(List.of(uni));
        when(areaRepository.findByUniversityIdInAndIsActiveTrue(List.of(UNIVERSITY_ID)))
                .thenReturn(List.of(area));
        when(careerRepository.findByAreaIdInAndIsActiveTrue(List.of(AREA_ID)))
                .thenReturn(List.of(career));

        List<CareerResponse> result = simulacroService.getCareers(ACADEMY_ID);

        assertThat(result).hasSize(1);
        CareerResponse r = result.getFirst();
        assertThat(r.id()).isEqualTo(CAREER_ID);
        assertThat(r.name()).isEqualTo("Medicina Humana");
        assertThat(r.areaName()).isEqualTo("Ciencias de la Salud");
        assertThat(r.universityName()).isEqualTo("Universidad Nacional");
    }

    // =========================================================================
    // getSimulacros() — published exams for an area
    // =========================================================================

    @Test
    @DisplayName("getSimulacros: area not found → NotFoundException")
    void getSimulacros_areaNotFound_throwsNotFoundException() {
        when(areaRepository.findByIdAndAcademyId(AREA_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> simulacroService.getSimulacros(AREA_ID, ACADEMY_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(String.valueOf(AREA_ID));
    }

    @Test
    @DisplayName("getSimulacros: area found but no exams → empty list")
    void getSimulacros_noExams_returnsEmpty() {
        SimulacroArea area = SimulacroArea.builder()
                .id(AREA_ID).academyId(ACADEMY_ID).universityId(UNIVERSITY_ID).name("Ciencias de la Salud").build();
        when(areaRepository.findByIdAndAcademyId(AREA_ID, ACADEMY_ID))
                .thenReturn(Optional.of(area));
        when(examRepository.findSimulacrosForArea(eq(ACADEMY_ID), eq(AREA_ID)))
                .thenReturn(List.of());

        List<SimulacroSummaryResponse> result = simulacroService.getSimulacros(AREA_ID, ACADEMY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getSimulacros: area with linked exams → returns summary list")
    void getSimulacros_withExams_returnsSummaries() {
        SimulacroArea area = SimulacroArea.builder()
                .id(AREA_ID).academyId(ACADEMY_ID).universityId(UNIVERSITY_ID).name("Ciencias de la Salud").build();
        Exam exam = Exam.builder()
                .id(EXAM_ID).uuid(UUID.randomUUID()).academyId(ACADEMY_ID)
                .scopeType("SIMULACRO").title("Simulacro Final")
                .status("PUBLISHED").durationMinutes(60).maxAttempts(3)
                .passingScore(new BigDecimal("60")).build();

        when(areaRepository.findByIdAndAcademyId(AREA_ID, ACADEMY_ID))
                .thenReturn(Optional.of(area));
        when(examRepository.findSimulacrosForArea(eq(ACADEMY_ID), eq(AREA_ID)))
                .thenReturn(List.of(exam));

        List<SimulacroSummaryResponse> result = simulacroService.getSimulacros(AREA_ID, ACADEMY_ID);

        assertThat(result).hasSize(1);
        SimulacroSummaryResponse r = result.getFirst();
        assertThat(r.title()).isEqualTo("Simulacro Final");
        assertThat(r.scopeType()).isEqualTo("SIMULACRO");
        assertThat(r.areaName()).isEqualTo("Ciencias de la Salud");
        assertThat(r.maxAttempts()).isEqualTo(3);
    }

    // =========================================================================
    // University CRUD
    // =========================================================================

    @Test
    @DisplayName("createUniversity: persists and returns the entity")
    void createUniversity_persistsAndReturns() {
        CreateUniversityRequest request = new CreateUniversityRequest("Universidad Nacional", "Descripción");

        SimulacroUniversity saved = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("Universidad Nacional")
                .description("Descripción").isActive(true).build();
        when(universityRepository.save(any(SimulacroUniversity.class)))
                .thenReturn(saved);

        SimulacroUniversity result = simulacroService.createUniversity(request, ACADEMY_ID);

        assertThat(result.getId()).isEqualTo(UNIVERSITY_ID);
        assertThat(result.getName()).isEqualTo("Universidad Nacional");
        verify(universityRepository).save(universityCaptor.capture());
        assertThat(universityCaptor.getValue().getAcademyId()).isEqualTo(ACADEMY_ID);
    }

    @Test
    @DisplayName("updateUniversity: not found → NotFoundException")
    void updateUniversity_notFound_throwsException() {
        when(universityRepository.findByIdAndAcademyId(UNIVERSITY_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> simulacroService.updateUniversity(UNIVERSITY_ID,
                new CreateUniversityRequest("Nuevo", null), ACADEMY_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("updateUniversity: found → updates name and description")
    void updateUniversity_found_updatesFields() {
        SimulacroUniversity existing = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("Antiguo").build();
        when(universityRepository.findByIdAndAcademyId(UNIVERSITY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(existing));

        CreateUniversityRequest request = new CreateUniversityRequest("Nuevo Nombre", "Nueva descripción");
        when(universityRepository.save(any(SimulacroUniversity.class)))
                .thenReturn(existing);

        simulacroService.updateUniversity(UNIVERSITY_ID, request, ACADEMY_ID);

        assertThat(existing.getName()).isEqualTo("Nuevo Nombre");
        assertThat(existing.getDescription()).isEqualTo("Nueva descripción");
        verify(universityRepository).save(existing);
    }

    @Test
    @DisplayName("deactivateUniversity: sets isActive to false")
    void deactivateUniversity_setsInactive() {
        SimulacroUniversity existing = SimulacroUniversity.builder()
                .id(UNIVERSITY_ID).academyId(ACADEMY_ID).name("UNMSM")
                .isActive(true).build();
        when(universityRepository.findByIdAndAcademyId(UNIVERSITY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(existing));

        simulacroService.deactivateUniversity(UNIVERSITY_ID, ACADEMY_ID);

        assertThat(existing.getIsActive()).isFalse();
        verify(universityRepository).save(existing);
    }

    // =========================================================================
    // Area CRUD
    // =========================================================================

    @Test
    @DisplayName("createArea: verifies university exists, then persists")
    void createArea_verifiesUniversity_persists() {
        when(universityRepository.findByIdAndAcademyId(UNIVERSITY_ID, ACADEMY_ID))
                .thenReturn(Optional.of(SimulacroUniversity.builder().id(UNIVERSITY_ID).build()));

        CreateAreaRequest request = new CreateAreaRequest(UNIVERSITY_ID, "Ciencias", "Descripción");
        SimulacroArea saved = SimulacroArea.builder()
                .id(AREA_ID).academyId(ACADEMY_ID).universityId(UNIVERSITY_ID)
                .name("Ciencias").build();
        when(areaRepository.save(any(SimulacroArea.class))).thenReturn(saved);

        SimulacroArea result = simulacroService.createArea(request, ACADEMY_ID);

        assertThat(result.getId()).isEqualTo(AREA_ID);
        assertThat(result.getUniversityId()).isEqualTo(UNIVERSITY_ID);
        verify(areaRepository).save(areaCaptor.capture());
        assertThat(areaCaptor.getValue().getAcademyId()).isEqualTo(ACADEMY_ID);
    }

    @Test
    @DisplayName("createArea: university not found → NotFoundException")
    void createArea_universityNotFound_throwsException() {
        when(universityRepository.findByIdAndAcademyId(UNIVERSITY_ID, ACADEMY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> simulacroService.createArea(
                new CreateAreaRequest(UNIVERSITY_ID, "Ciencias", null), ACADEMY_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // =========================================================================
    // Career CRUD
    // =========================================================================

    @Test
    @DisplayName("createCareer: verifies area exists, then persists")
    void createCareer_verifiesArea_persists() {
        when(areaRepository.findByIdAndAcademyId(AREA_ID, ACADEMY_ID))
                .thenReturn(Optional.of(SimulacroArea.builder().id(AREA_ID).build()));

        CreateCareerRequest request = new CreateCareerRequest(AREA_ID, "Medicina", "Descripción", null);
        SimulacroCareer saved = SimulacroCareer.builder()
                .id(CAREER_ID).academyId(ACADEMY_ID).areaId(AREA_ID)
                .name("Medicina").build();
        when(careerRepository.save(any(SimulacroCareer.class))).thenReturn(saved);

        SimulacroCareer result = simulacroService.createCareer(request, ACADEMY_ID);

        assertThat(result.getId()).isEqualTo(CAREER_ID);
        assertThat(result.getAreaId()).isEqualTo(AREA_ID);
        verify(careerRepository).save(careerCaptor.capture());
        assertThat(careerCaptor.getValue().getAcademyId()).isEqualTo(ACADEMY_ID);
    }

    @Test
    @DisplayName("deactivateCareer: sets isActive to false")
    void deactivateCareer_setsInactive() {
        SimulacroCareer existing = SimulacroCareer.builder()
                .id(CAREER_ID).academyId(ACADEMY_ID).areaId(AREA_ID)
                .name("Medicina").isActive(true).build();
        when(careerRepository.findByIdAndAcademyId(CAREER_ID, ACADEMY_ID))
                .thenReturn(Optional.of(existing));

        simulacroService.deactivateCareer(CAREER_ID, ACADEMY_ID);

        assertThat(existing.getIsActive()).isFalse();
        verify(careerRepository).save(existing);
    }

    // =========================================================================
    // Exam-Area linking
    // =========================================================================

    @Test
    @DisplayName("linkExamToArea: saves mapping when not exists")
    void linkExamToArea_savesMapping() {
        ExamAreaMappingId mappingId = new ExamAreaMappingId(EXAM_ID, AREA_ID);
        when(examAreaMappingRepository.existsById(mappingId)).thenReturn(false);
        when(examAreaMappingRepository.save(any(ExamAreaMapping.class)))
                .thenReturn(new ExamAreaMapping(mappingId));

        simulacroService.linkExamToArea(AREA_ID, EXAM_ID);

        verify(examAreaMappingRepository).save(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getId().getExamId()).isEqualTo(EXAM_ID);
        assertThat(mappingCaptor.getValue().getId().getAreaId()).isEqualTo(AREA_ID);
    }

    @Test
    @DisplayName("linkExamToArea: skips save when mapping already exists")
    void linkExamToArea_skipsIfExists() {
        ExamAreaMappingId mappingId = new ExamAreaMappingId(EXAM_ID, AREA_ID);
        when(examAreaMappingRepository.existsById(mappingId)).thenReturn(true);

        simulacroService.linkExamToArea(AREA_ID, EXAM_ID);

        verify(examAreaMappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("unlinkExamFromArea: deletes mapping by id")
    void unlinkExamFromArea_deletesMapping() {
        simulacroService.unlinkExamFromArea(AREA_ID, EXAM_ID);

        ExamAreaMappingId expectedId = new ExamAreaMappingId(EXAM_ID, AREA_ID);
        verify(examAreaMappingRepository).deleteById(expectedId);
    }

    // =========================================================================
    // getLinkedAreas() — admin view
    // =========================================================================

    @Test
    @DisplayName("getLinkedAreas: no mappings → empty list")
    void getLinkedAreas_noMappings_returnsEmpty() {
        when(examAreaMappingRepository.findByExamId(EXAM_ID))
                .thenReturn(List.of());

        List<AreaResponse> result = simulacroService.getLinkedAreas(EXAM_ID, ACADEMY_ID);

        assertThat(result).isEmpty();
    }
}

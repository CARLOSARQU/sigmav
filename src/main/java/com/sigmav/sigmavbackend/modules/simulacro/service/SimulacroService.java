package com.sigmav.sigmavbackend.modules.simulacro.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulacroService {

    private final SimulacroUniversityRepository universityRepository;
    private final SimulacroAreaRepository areaRepository;
    private final SimulacroCareerRepository careerRepository;
    private final ExamRepository examRepository;
    private final ExamAreaMappingRepository examAreaMappingRepository;

    // --- Student-facing queries ---

    /**
     * All active careers for an academy, enriched with area name and university name.
     */
    public List<CareerResponse> getCareers(Long academyId) {
        List<SimulacroUniversity> universities = universityRepository
                .findByAcademyIdAndIsActiveTrueOrderByName(academyId);
        if (universities.isEmpty()) return List.of();

        // Map: universityId -> universityName
        Map<Long, String> uniNameById = universities.stream()
                .collect(Collectors.toMap(SimulacroUniversity::getId, SimulacroUniversity::getName));

        List<Long> uniIds = universities.stream().map(SimulacroUniversity::getId).toList();
        List<SimulacroArea> areas = areaRepository.findByUniversityIdInAndIsActiveTrue(uniIds);
        if (areas.isEmpty()) return List.of();

        // Map: areaId -> areaName
        Map<Long, String> areaNameById = areas.stream()
                .collect(Collectors.toMap(SimulacroArea::getId, SimulacroArea::getName));
        // Map: areaId -> universityId
        Map<Long, Long> uniIdByAreaId = areas.stream()
                .collect(Collectors.toMap(SimulacroArea::getId, SimulacroArea::getUniversityId));

        List<Long> areaIds = areas.stream().map(SimulacroArea::getId).toList();
        List<SimulacroCareer> careers = careerRepository.findByAreaIdInAndIsActiveTrue(areaIds);

        return careers.stream().map(c -> new CareerResponse(
                c.getId(),
                c.getName(),
                areaNameById.getOrDefault(c.getAreaId(), ""),
                uniNameById.getOrDefault(uniIdByAreaId.get(c.getAreaId()), ""),
                c.getMinimumScore()
        )).toList();
    }

    /**
     * All active areas for an academy, enriched with university name.
     */
    public List<AreaResponse> getAreas(Long academyId) {
        List<SimulacroUniversity> universities = universityRepository
                .findByAcademyIdAndIsActiveTrueOrderByName(academyId);
        if (universities.isEmpty()) return List.of();

        Map<Long, String> uniNameById = universities.stream()
                .collect(Collectors.toMap(SimulacroUniversity::getId, SimulacroUniversity::getName));

        List<Long> uniIds = universities.stream().map(SimulacroUniversity::getId).toList();
        List<SimulacroArea> areas = areaRepository.findByUniversityIdInAndIsActiveTrue(uniIds);

        return areas.stream().map(a -> new AreaResponse(
                a.getId(),
                a.getName(),
                uniNameById.getOrDefault(a.getUniversityId(), ""),
                List.of() // no careers in the flat area list
        )).toList();
    }

    /**
     * Published SIMULACRO exams linked to an area, with area info.
     */
    public List<SimulacroSummaryResponse> getSimulacros(Long areaId, Long academyId) {
        SimulacroArea area = areaRepository.findByIdAndAcademyId(areaId, academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + areaId));

        List<com.sigmav.sigmavbackend.modules.exam.entity.Exam> exams =
                examRepository.findSimulacrosForArea(academyId, areaId);

        return exams.stream().map(e -> new SimulacroSummaryResponse(
                e.getId(), e.getUuid(), e.getScopeType(),
                e.getTitle(), e.getStatus(),
                e.getAvailableFrom(), e.getAvailableUntil(),
                e.getDurationMinutes(), e.getMaxAttempts(), e.getPassingScore(),
                e.getCreatedAt(),
                areaId, area.getName()
        )).toList();
    }

    /**
     * Active universities with their areas and careers (nested cascade for admin).
     */
    public List<UniversityResponse> getUniversities(Long academyId) {
        List<SimulacroUniversity> universities = universityRepository
                .findByAcademyIdAndIsActiveTrueOrderByName(academyId);
        if (universities.isEmpty()) return List.of();

        List<Long> uniIds = universities.stream().map(SimulacroUniversity::getId).toList();
        List<SimulacroArea> allAreas = areaRepository.findByUniversityIdInAndIsActiveTrue(uniIds);
        Map<Long, List<SimulacroArea>> areaByUni = allAreas.stream()
                .collect(Collectors.groupingBy(SimulacroArea::getUniversityId));

        List<Long> areaIds = allAreas.stream().map(SimulacroArea::getId).toList();
        List<SimulacroCareer> allCareers = areaIds.isEmpty() ? List.of()
                : careerRepository.findByAreaIdInAndIsActiveTrue(areaIds);
        Map<Long, List<SimulacroCareer>> careerByArea = allCareers.stream()
                .collect(Collectors.groupingBy(SimulacroCareer::getAreaId));

        return universities.stream().map(uni -> new UniversityResponse(
                uni.getId(), uni.getName(),
                areaByUni.getOrDefault(uni.getId(), List.of()).stream()
                        .map(area -> new AreaResponse(
                                area.getId(), area.getName(), uni.getName(),
                                careerByArea.getOrDefault(area.getId(), List.of()).stream()
                                        .map(c -> new CareerResponse(c.getId(), c.getName(), area.getName(), uni.getName(), c.getMinimumScore()))
                                        .toList()
                        ))
                        .toList()
        )).toList();
    }

    // --- Admin: University CRUD ---

    public List<SimulacroUniversity> listUniversities(Long academyId) {
        return universityRepository.findByAcademyIdOrderByName(academyId);
    }

    @Transactional
    public SimulacroUniversity createUniversity(CreateUniversityRequest request, Long academyId) {
        SimulacroUniversity entity = SimulacroUniversity.builder()
                .academyId(academyId)
                .name(request.name())
                .description(request.description())
                .build();
        return universityRepository.save(entity);
    }

    @Transactional
    public SimulacroUniversity updateUniversity(Long id, CreateUniversityRequest request, Long academyId) {
        SimulacroUniversity uni = universityRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Universidad no encontrada con ID: " + id));
        uni.setName(request.name());
        uni.setDescription(request.description());
        return universityRepository.save(uni);
    }

    @Transactional
    public void deactivateUniversity(Long id, Long academyId) {
        SimulacroUniversity uni = universityRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Universidad no encontrada con ID: " + id));
        uni.setIsActive(false);
        universityRepository.save(uni);
    }

    // --- Admin: Area CRUD ---

    public List<SimulacroArea> listAreas(Long universityId, Long academyId) {
        // Verify university belongs to academy
        universityRepository.findByIdAndAcademyId(universityId, academyId)
                .orElseThrow(() -> new NotFoundException("Universidad no encontrada con ID: " + universityId));
        return areaRepository.findByUniversityIdOrderByName(universityId);
    }

    @Transactional
    public SimulacroArea createArea(CreateAreaRequest request, Long academyId) {
        // Verify university belongs to academy
        universityRepository.findByIdAndAcademyId(request.universityId(), academyId)
                .orElseThrow(() -> new NotFoundException("Universidad no encontrada con ID: " + request.universityId()));

        SimulacroArea entity = SimulacroArea.builder()
                .academyId(academyId)
                .universityId(request.universityId())
                .name(request.name())
                .description(request.description())
                .build();
        return areaRepository.save(entity);
    }

    @Transactional
    public SimulacroArea updateArea(Long id, CreateAreaRequest request, Long academyId) {
        SimulacroArea area = areaRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + id));
        area.setName(request.name());
        area.setDescription(request.description());
        return areaRepository.save(area);
    }

    @Transactional
    public void deactivateArea(Long id, Long academyId) {
        SimulacroArea area = areaRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + id));
        area.setIsActive(false);
        areaRepository.save(area);
    }

    // --- Admin: Career CRUD ---

    public List<SimulacroCareer> listCareers(Long areaId, Long academyId) {
        // Verify area belongs to academy
        areaRepository.findByIdAndAcademyId(areaId, academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + areaId));
        return careerRepository.findByAreaIdOrderByName(areaId);
    }

    @Transactional
    public SimulacroCareer createCareer(CreateCareerRequest request, Long academyId) {
        // Verify area belongs to academy
        areaRepository.findByIdAndAcademyId(request.areaId(), academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + request.areaId()));

        SimulacroCareer entity = SimulacroCareer.builder()
                .academyId(academyId)
                .areaId(request.areaId())
                .name(request.name())
                .description(request.description())
                .minimumScore(request.minimumScore())
                .build();
        return careerRepository.save(entity);
    }

    @Transactional
    public SimulacroCareer updateCareer(Long id, CreateCareerRequest request, Long academyId) {
        SimulacroCareer career = careerRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Carrera no encontrada con ID: " + id));
        career.setName(request.name());
        career.setDescription(request.description());
        career.setMinimumScore(request.minimumScore());
        return careerRepository.save(career);
    }

    @Transactional
    public void deactivateCareer(Long id, Long academyId) {
        SimulacroCareer career = careerRepository.findByIdAndAcademyId(id, academyId)
                .orElseThrow(() -> new NotFoundException("Carrera no encontrada con ID: " + id));
        career.setIsActive(false);
        careerRepository.save(career);
    }

    // --- Admin: Exam-Area linking ---

    /**
     * Get areas linked to an exam, with university names.
     */
    public List<AreaResponse> getLinkedAreas(Long examId, Long academyId) {
        List<ExamAreaMapping> mappings = examAreaMappingRepository.findByExamId(examId);
        if (mappings.isEmpty()) return List.of();

        List<Long> areaIds = mappings.stream()
                .map(m -> m.getId().getAreaId())
                .toList();
        List<SimulacroArea> areas = areaRepository.findAllById(areaIds);

        // Build university name map
        List<Long> uniIds = areas.stream().map(SimulacroArea::getUniversityId).distinct().toList();
        List<SimulacroUniversity> universities = universityRepository.findAllById(uniIds);
        Map<Long, String> uniNameById = universities.stream()
                .collect(Collectors.toMap(SimulacroUniversity::getId, SimulacroUniversity::getName));

        return areas.stream().map(a -> new AreaResponse(
                a.getId(),
                a.getName(),
                uniNameById.getOrDefault(a.getUniversityId(), ""),
                List.of() // no careers in the area linking view
        )).toList();
    }

    /**
     * Link an exam to an area.
     */
    @Transactional
    public void linkExamToArea(Long areaId, Long examId) {
        ExamAreaMappingId id = new ExamAreaMappingId(examId, areaId);
        if (!examAreaMappingRepository.existsById(id)) {
            examAreaMappingRepository.save(new ExamAreaMapping(id));
        }
    }

    /**
     * Unlink an exam from an area.
     */
    @Transactional
    public void unlinkExamFromArea(Long areaId, Long examId) {
        ExamAreaMappingId id = new ExamAreaMappingId(examId, areaId);
        examAreaMappingRepository.deleteById(id);
    }

    /**
     * Career cut-off comparison for a given area.
     * Returns all active careers for the area with their minimum scores,
     * compared against the student's score to determine pass/fail.
     */
    public CareerThresholdResponse getCareerThresholds(Long areaId, double studentScore, Long academyId) {
        SimulacroArea area = areaRepository.findByIdAndAcademyId(areaId, academyId)
                .orElseThrow(() -> new NotFoundException("Área no encontrada con ID: " + areaId));

        SimulacroUniversity uni = universityRepository.findById(area.getUniversityId())
                .orElseThrow(() -> new NotFoundException("Universidad no encontrada"));

        List<SimulacroCareer> careers = careerRepository.findByAreaIdAndIsActiveTrueOrderByName(areaId);

        List<CareerThresholdResponse.CareerThreshold> thresholds = careers.stream()
                .map(c -> new CareerThresholdResponse.CareerThreshold(
                        c.getId(),
                        c.getName(),
                        c.getMinimumScore(),
                        studentScore,
                        c.getMinimumScore() != null && studentScore >= c.getMinimumScore().doubleValue()
                ))
                .toList();

        return new CareerThresholdResponse(
                area.getId(),
                area.getName(),
                uni.getName(),
                thresholds
        );
    }
}

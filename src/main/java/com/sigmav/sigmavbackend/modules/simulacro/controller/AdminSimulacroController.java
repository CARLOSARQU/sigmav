package com.sigmav.sigmavbackend.modules.simulacro.controller;

import com.sigmav.sigmavbackend.config.aop.RequiresFeature;
import com.sigmav.sigmavbackend.modules.academy.entity.Feature;
import com.sigmav.sigmavbackend.modules.simulacro.dto.*;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroArea;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroCareer;
import com.sigmav.sigmavbackend.modules.simulacro.entity.SimulacroUniversity;
import com.sigmav.sigmavbackend.modules.simulacro.service.SimulacroService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin endpoints for simulacro (mock exam) configuration management.
 * Requires MANAGE_SIMULACROS or MANAGE_EXAMS permission and EXAMS feature flag.
 */
@RestController
@RequestMapping("/api/admin/simulacro")
@RequiredArgsConstructor
public class AdminSimulacroController {

    private final SimulacroService simulacroService;

    // --- Universities ---

    @GetMapping("/universities")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<List<SimulacroUniversity>> listUniversities(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(simulacroService.listUniversities(principal.getAcademyId()));
    }

    @PostMapping("/universities")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroUniversity> createUniversity(
            @Valid @RequestBody CreateUniversityRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulacroService.createUniversity(request, principal.getAcademyId()));
    }

    @PatchMapping("/universities/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroUniversity> updateUniversity(
            @PathVariable Long id,
            @Valid @RequestBody CreateUniversityRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.updateUniversity(id, request, principal.getAcademyId()));
    }

    @DeleteMapping("/universities/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<Void> deactivateUniversity(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        simulacroService.deactivateUniversity(id, principal.getAcademyId());
        return ResponseEntity.noContent().build();
    }

    // --- Areas ---

    @GetMapping("/areas")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<List<SimulacroArea>> listAreas(
            @RequestParam Long universityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.listAreas(universityId, principal.getAcademyId()));
    }

    @PostMapping("/areas")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroArea> createArea(
            @Valid @RequestBody CreateAreaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulacroService.createArea(request, principal.getAcademyId()));
    }

    @PatchMapping("/areas/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroArea> updateArea(
            @PathVariable Long id,
            @Valid @RequestBody CreateAreaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.updateArea(id, request, principal.getAcademyId()));
    }

    @DeleteMapping("/areas/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<Void> deactivateArea(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        simulacroService.deactivateArea(id, principal.getAcademyId());
        return ResponseEntity.noContent().build();
    }

    // --- Careers ---

    @GetMapping("/careers")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<List<SimulacroCareer>> listCareers(
            @RequestParam Long areaId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.listCareers(areaId, principal.getAcademyId()));
    }

    @PostMapping("/careers")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroCareer> createCareer(
            @Valid @RequestBody CreateCareerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulacroService.createCareer(request, principal.getAcademyId()));
    }

    @PatchMapping("/careers/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<SimulacroCareer> updateCareer(
            @PathVariable Long id,
            @Valid @RequestBody CreateCareerRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.updateCareer(id, request, principal.getAcademyId()));
    }

    @DeleteMapping("/careers/{id}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<Void> deactivateCareer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        simulacroService.deactivateCareer(id, principal.getAcademyId());
        return ResponseEntity.noContent().build();
    }

    // --- Exam-Area linking ---

    /**
     * Get areas linked to an exam.
     */
    @GetMapping("/exams/{examId}/areas")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<List<AreaResponse>> getLinkedAreas(
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.getLinkedAreas(examId, principal.getAcademyId()));
    }

    /**
     * Full cascade: universities with their areas and careers (for admin config UI).
     */
    @GetMapping("/universities/cascade")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<List<UniversityResponse>> getUniversitiesCascade(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(simulacroService.getUniversities(principal.getAcademyId()));
    }

    /**
     * Link an exam to an area.
     */
    @PostMapping("/areas/{areaId}/exams/{examId}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<Void> linkExamToArea(
            @PathVariable Long areaId,
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {
        simulacroService.linkExamToArea(areaId, examId);
        return ResponseEntity.ok().build();
    }

    /**
     * Unlink an exam from an area.
     */
    @DeleteMapping("/areas/{areaId}/exams/{examId}")
    @RequiresFeature(Feature.EXAMS)
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SIMULACROS') or hasAuthority('PERMISSION_MANAGE_EXAMS')")
    public ResponseEntity<Void> unlinkExamFromArea(
            @PathVariable Long areaId,
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {
        simulacroService.unlinkExamFromArea(areaId, examId);
        return ResponseEntity.noContent().build();
    }
}

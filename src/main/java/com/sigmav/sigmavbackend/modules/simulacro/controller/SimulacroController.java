package com.sigmav.sigmavbackend.modules.simulacro.controller;

import com.sigmav.sigmavbackend.modules.simulacro.dto.CareerThresholdResponse;
import com.sigmav.sigmavbackend.modules.simulacro.dto.SimulacroSummaryResponse;
import com.sigmav.sigmavbackend.modules.simulacro.dto.UniversityResponse;
import com.sigmav.sigmavbackend.modules.simulacro.service.SimulacroService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student-facing endpoints for simulacros (mock exams).
 * Students can browse areas and available simulacros without enrollment.
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class SimulacroController {

    private final SimulacroService simulacroService;

    /**
     * Full cascade: universities with their areas (for the student cascade selector).
     */
    @GetMapping("/areas")
    public ResponseEntity<List<UniversityResponse>> getAreas(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(simulacroService.getUniversities(principal.getAcademyId()));
    }

    /**
     * List published SIMULACRO exams linked to a specific area.
     */
    @GetMapping("/simulacros")
    public ResponseEntity<List<SimulacroSummaryResponse>> getSimulacros(
            @RequestParam Long areaId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.getSimulacros(areaId, principal.getAcademyId()));
    }

    /**
     * Career cut-off comparison for a given area.
     * After completing a simulacro, the student can see which careers
     * they would qualify for based on their score vs. per-career minimums.
     */
    @GetMapping("/simulacros/careers/{areaId}/thresholds")
    public ResponseEntity<CareerThresholdResponse> getCareerThresholds(
            @PathVariable Long areaId,
            @RequestParam double score,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                simulacroService.getCareerThresholds(areaId, score, principal.getAcademyId()));
    }
}

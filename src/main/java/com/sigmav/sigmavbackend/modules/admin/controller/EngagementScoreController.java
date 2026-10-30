package com.sigmav.sigmavbackend.modules.admin.controller;

import com.sigmav.sigmavbackend.config.aop.RequiresFeature;
import com.sigmav.sigmavbackend.modules.academy.entity.Feature;
import com.sigmav.sigmavbackend.modules.admin.dto.EngagementScoreResponse;
import com.sigmav.sigmavbackend.modules.admin.service.EngagementScoreService;
import com.sigmav.sigmavbackend.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/engagement")
@RequiredArgsConstructor
public class EngagementScoreController {

    private final EngagementScoreService engagementScoreService;

    @GetMapping("/scores")
    @PreAuthorize("hasAuthority('PERMISSION_VIEW_REPORTS')")
    @RequiresFeature(Feature.ENGAGEMENT_SCORE)
    public ResponseEntity<EngagementScoreResponse> getScores() {
        Long academyId = TenantContext.getCurrentAcademyId();
        return ResponseEntity.ok(engagementScoreService.getEngagementScores(academyId));
    }
}

package com.sigmav.sigmavbackend.modules.course.controller;

import com.sigmav.sigmavbackend.modules.course.dto.CreateSectionRequest;
import com.sigmav.sigmavbackend.modules.course.dto.SectionResponse;
import com.sigmav.sigmavbackend.modules.course.dto.UpdateSectionRequest;
import com.sigmav.sigmavbackend.modules.course.service.CourseSectionService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CourseSectionController {

    private final CourseSectionService sectionService;

    @GetMapping("/api/courses/{courseId}/sections")
    public ResponseEntity<List<SectionResponse>> listByCourse(@PathVariable Long courseId,
                                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sectionService.listByCourse(courseId, principal));
    }

    @GetMapping("/api/sections/{id}")
    public ResponseEntity<SectionResponse> getById(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sectionService.getById(id, principal));
    }

    @PostMapping("/api/courses/{courseId}/sections")
    public ResponseEntity<SectionResponse> create(@PathVariable Long courseId,
                                                  @Valid @RequestBody CreateSectionRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sectionService.create(courseId, request, principal));
    }

    @PatchMapping("/api/sections/{id}")
    public ResponseEntity<SectionResponse> update(@PathVariable Long id,
                                                  @RequestBody UpdateSectionRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sectionService.update(id, request, principal));
    }
}

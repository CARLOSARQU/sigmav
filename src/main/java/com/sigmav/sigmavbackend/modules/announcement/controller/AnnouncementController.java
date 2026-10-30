package com.sigmav.sigmavbackend.modules.announcement.controller;

import com.sigmav.sigmavbackend.modules.announcement.dto.AnnouncementResponse;
import com.sigmav.sigmavbackend.modules.announcement.dto.CreateAnnouncementRequest;
import com.sigmav.sigmavbackend.modules.announcement.service.AnnouncementService;
import com.sigmav.sigmavbackend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** Listar anuncios de un curso — acceso: admin, docente, estudiante matriculado */
    @GetMapping("/api/courses/{courseId}/announcements")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<AnnouncementResponse>> list(@PathVariable Long courseId) {
        return ResponseEntity.ok(announcementService.listByCourse(courseId));
    }

    /** Crear anuncio — solo admin y docente */
    @PostMapping("/api/courses/{courseId}/announcements")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<AnnouncementResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateAnnouncementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(announcementService.create(courseId, request, principal.getId()));
    }

    /** Eliminar (desactivar) anuncio — solo admin y docente */
    @DeleteMapping("/api/announcements/{id}")
    @PreAuthorize("hasAnyRole('ACADEMY_ADMIN', 'TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok().build();
    }
}

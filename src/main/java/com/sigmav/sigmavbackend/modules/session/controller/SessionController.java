package com.sigmav.sigmavbackend.modules.session.controller;

import com.sigmav.sigmavbackend.modules.session.dto.*;
import com.sigmav.sigmavbackend.modules.session.service.AttendanceService;
import com.sigmav.sigmavbackend.modules.session.service.ClassSessionService;
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
public class SessionController {

    private final ClassSessionService sessionService;
    private final AttendanceService attendanceService;

    // --- CRUD Sesiones (B6.2) ---

    @GetMapping("/api/courses/{courseId}/sessions")
    public ResponseEntity<List<SessionResponse>> listByCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.listByCourse(courseId, principal));
    }

    @GetMapping("/api/sessions/{id}")
    public ResponseEntity<SessionResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.getById(id, principal));
    }

    @PostMapping("/api/courses/{courseId}/sessions")
    public ResponseEntity<SessionResponse> create(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.create(courseId, request, principal));
    }

    @PatchMapping("/api/sessions/{id}")
    public ResponseEntity<SessionResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateSessionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.update(id, request, principal));
    }

    // --- QR (B6.3) ---

    @GetMapping("/api/sessions/{id}/qr")
    public ResponseEntity<QrTokenResponse> getQrToken(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(sessionService.getQrToken(id, principal));
    }

    // --- Asistencia para docente/admin (B6.7) ---

    @GetMapping("/api/sessions/{id}/attendance")
    public ResponseEntity<List<AttendanceListItemResponse>> getAttendanceList(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(attendanceService.getAttendanceList(id, principal));
    }

    @PatchMapping("/api/sessions/{id}/attendance/{studentId}")
    public ResponseEntity<Void> updateAttendanceStatus(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @Valid @RequestBody UpdateAttendanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.updateAttendanceStatus(id, studentId, request, principal);
        return ResponseEntity.noContent().build();
    }
}

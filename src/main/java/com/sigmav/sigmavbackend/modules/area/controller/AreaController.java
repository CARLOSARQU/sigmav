package com.sigmav.sigmavbackend.modules.area.controller;

import com.sigmav.sigmavbackend.modules.area.dto.AreaResponse;
import com.sigmav.sigmavbackend.modules.area.dto.CreateAreaRequest;
import com.sigmav.sigmavbackend.modules.area.dto.UpdateAreaRequest;
import com.sigmav.sigmavbackend.modules.area.service.AreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    /** Lista áreas de un ciclo */
    @GetMapping("/api/cycles/{cycleId}/areas")
    public ResponseEntity<List<AreaResponse>> listByCycle(@PathVariable Long cycleId) {
        return ResponseEntity.ok(areaService.listByCycle(cycleId));
    }

    @GetMapping("/api/areas/{id}")
    public ResponseEntity<AreaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(areaService.getById(id));
    }

    @PostMapping("/api/cycles/{cycleId}/areas")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<AreaResponse> create(@PathVariable Long cycleId,
                                               @Valid @RequestBody CreateAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(areaService.create(cycleId, request));
    }

    @PatchMapping("/api/areas/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<AreaResponse> update(@PathVariable Long id,
                                               @RequestBody UpdateAreaRequest request) {
        return ResponseEntity.ok(areaService.update(id, request));
    }

    @DeleteMapping("/api/areas/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_ACADEMY_CONFIG')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        areaService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

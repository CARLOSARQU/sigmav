package com.sigmav.sigmavbackend.modules.cycle.controller;

import com.sigmav.sigmavbackend.modules.cycle.dto.CreateCycleRequest;
import com.sigmav.sigmavbackend.modules.cycle.dto.CycleResponse;
import com.sigmav.sigmavbackend.modules.cycle.dto.UpdateCycleRequest;
import com.sigmav.sigmavbackend.modules.cycle.service.CycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cycles")
@RequiredArgsConstructor
public class CycleController {

    private final CycleService cycleService;

    @GetMapping
    public ResponseEntity<List<CycleResponse>> listAll() {
        return ResponseEntity.ok(cycleService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CycleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(cycleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<CycleResponse> create(@Valid @RequestBody CreateCycleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cycleService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<CycleResponse> update(@PathVariable Long id,
                                                @RequestBody UpdateCycleRequest request) {
        return ResponseEntity.ok(cycleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_MANAGE_SCHEDULE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        cycleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}

package com.sigmav.sigmavbackend.modules.broadcast.controller;

import com.sigmav.sigmavbackend.modules.broadcast.dto.AdminBroadcastRequest;
import com.sigmav.sigmavbackend.modules.broadcast.dto.SuperAdminBroadcastRequest;
import com.sigmav.sigmavbackend.modules.broadcast.service.BroadcastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BroadcastController {

    private final BroadcastService broadcastService;

    /** Admin envía difusión dentro de su academia */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ACADEMY_ADMIN')")
    public ResponseEntity<Map<String, Integer>> adminBroadcast(@Valid @RequestBody AdminBroadcastRequest req) {
        int sent = broadcastService.sendAdminBroadcast(req);
        return ResponseEntity.ok(Map.of("sent", sent));
    }

    /** Superadmin envía difusión a una o todas las academias */
    @PostMapping("/superadmin/broadcast")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Integer>> superAdminBroadcast(@Valid @RequestBody SuperAdminBroadcastRequest req) {
        int sent = broadcastService.sendSuperAdminBroadcast(req);
        return ResponseEntity.ok(Map.of("sent", sent));
    }
}

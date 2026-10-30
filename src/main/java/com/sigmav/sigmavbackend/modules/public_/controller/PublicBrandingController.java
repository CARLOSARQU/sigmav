package com.sigmav.sigmavbackend.modules.public_.controller;

import com.sigmav.sigmavbackend.modules.public_.dto.BrandingResponse;
import com.sigmav.sigmavbackend.modules.public_.service.PublicBrandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicBrandingController {

    private final PublicBrandingService brandingService;

    /**
     * Cached in memory after first read — version changes require a restart.
     */
    private volatile String cachedVersion;

    @GetMapping("/resolve-email")
    public ResponseEntity<BrandingResponse> resolveByEmail(@RequestParam String email) {
        return ResponseEntity.ok(brandingService.getByEmail(email.trim().toLowerCase()));
    }

    @GetMapping("/branding")
    public ResponseEntity<BrandingResponse> getBranding(@RequestParam String subdomain) {
        return ResponseEntity.ok(brandingService.getBySubdomain(subdomain));
    }

    /** Resuelve branding por dominio personalizado (ej: diplomados.ciis-cpipuno.org.pe) */
    @GetMapping("/resolve-domain")
    public ResponseEntity<BrandingResponse> resolveByDomain(@RequestParam String domain) {
        return ResponseEntity.ok(brandingService.getByDomain(domain.trim().toLowerCase()));
    }

    @GetMapping("/version")
    public ResponseEntity<String> getVersion() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(getCachedVersion());
    }

    private String getCachedVersion() {
        if (cachedVersion == null) {
            synchronized (this) {
                if (cachedVersion == null) {
                    try {
                        cachedVersion = new String(
                                new ClassPathResource("version.txt").getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8
                        ).trim();
                    } catch (IOException e) {
                        log.warn("Could not read version.txt, falling back to dev", e);
                        cachedVersion = "dev";
                    }
                }
            }
        }
        return cachedVersion;
    }
}

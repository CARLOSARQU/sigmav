package com.sigmav.sigmavbackend.modules.public_.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.public_.dto.BrandingResponse;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicBrandingService {

    // Responses from this service are cached in Redis. The JSON serializer records
    // the concrete collection class, and JDK immutable collections (List.of, toList)
    // cannot be deserialized back — so cached payloads must use ArrayList copies.
    private static final List<String> DEFAULT_FEATURES = List.of(
            "Gestión de ciclos, áreas y cursos",
            "Exámenes con corrección automática",
            "Asistencia QR y reportes en tiempo real"
    );

    private final AcademyRepository academyRepository;
    private final UserRepository userRepository;

    @Cacheable(value = "branding", key = "#subdomain")
    public BrandingResponse getBySubdomain(String subdomain) {
        Academy academy = academyRepository.findBySubdomain(subdomain)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada"));
        return toResponse(academy);
    }

    /**
     * Resuelve branding por dominio personalizado (ej: diplomados.ciis-cpipuno.org.pe).
     * Busca en academy.config.customDomain vía consulta nativa sobre el JSONB.
     */
    public BrandingResponse getByDomain(String domain) {
        Academy academy = academyRepository.findByCustomDomain(domain)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada para el dominio: " + domain));
        return toResponse(academy);
    }

    @Cacheable(value = "branding-email", key = "#email")
    public BrandingResponse getByEmail(String email) {
        email = email.toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("No encontramos una cuenta con ese correo"));

        if (user.getAcademyId() == null) {
            return new BrandingResponse("Sigmav", null, "#6366f1", null, new ArrayList<>(DEFAULT_FEATURES));
        }

        Academy academy = academyRepository.findById(user.getAcademyId())
                .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
                .orElseThrow(() -> new NotFoundException("No encontramos una cuenta con ese correo"));
        return toResponse(academy);
    }

    @Caching(evict = {
        @CacheEvict(value = "branding",       allEntries = true),
        @CacheEvict(value = "branding-email", allEntries = true)
    })
    public void evictBrandingCache() {}

    private BrandingResponse toResponse(Academy academy) {
        return new BrandingResponse(
                academy.getName(),
                academy.getSubdomain(),
                getConfig(academy, "primaryColor", "#6366f1"),
                getConfig(academy, "logoUrl", null),
                getFeatures(academy)
        );
    }

    private String getConfig(Academy academy, String key, String defaultValue) {
        if (academy.getConfig() == null) return defaultValue;
        Object val = academy.getConfig().get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private List<String> getFeatures(Academy academy) {
        if (academy.getConfig() == null) return new ArrayList<>(DEFAULT_FEATURES);
        Object raw = academy.getConfig().get("loginFeatures");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>(DEFAULT_FEATURES);
    }
}

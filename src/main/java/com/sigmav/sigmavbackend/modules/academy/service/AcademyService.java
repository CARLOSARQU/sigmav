package com.sigmav.sigmavbackend.modules.academy.service;

import com.sigmav.sigmavbackend.modules.academy.dto.*;
import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.entity.AcademySubscription;
import com.sigmav.sigmavbackend.modules.academy.entity.Feature;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademySubscriptionRepository;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.security.TenantContext;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AcademyService {

    private final AcademyRepository academyRepository;
    private final AcademySubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Lazy self-reference so internal calls to getFeatures() go through the
    // Spring proxy — a direct this.getFeatures() call would bypass @Cacheable
    private final ObjectProvider<AcademyService> self;

    public Page<AcademyResponse> listAll(Boolean isActive, String planType, Pageable pageable) {
        Specification<Academy> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }
        if (StringUtils.hasText(planType)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("planType"), planType.toUpperCase()));
        }

        return academyRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public AcademyResponse getById(Long id) {
        return toResponse(findOrFail(id));
    }

    @Transactional
    public AcademyResponse create(CreateAcademyRequest request) {
        if (academyRepository.existsBySubdomain(request.subdomain())) {
            throw new IllegalArgumentException("El subdominio '" + request.subdomain() + "' ya está en uso");
        }

        Academy academy = Academy.builder()
                .name(request.name())
                .subdomain(request.subdomain().toLowerCase().trim())
                .planType(request.planType() != null ? request.planType() : "FREE")
                .maxStudents(request.maxStudents() != null ? request.maxStudents() : 50)
                .maxCourses(request.maxCourses() != null ? request.maxCourses() : 10)
                .maxStorageMb(request.maxStorageMb() != null ? request.maxStorageMb() : 500)
                .features(defaultFeatures())
                .build();

        Academy saved = academyRepository.save(academy);

        // Crear ACADEMY_ADMIN si se proporcionaron los datos del admin
        if (StringUtils.hasText(request.adminEmail()) && StringUtils.hasText(request.adminPassword())) {
            String adminEmail = request.adminEmail().toLowerCase().trim();
            if (userRepository.existsByEmailAndAcademyId(adminEmail, saved.getId())) {
                throw new IllegalArgumentException("Ya existe un usuario con ese email en la academia");
            }
            User admin = User.builder()
                    .academyId(saved.getId())
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(request.adminPassword()))
                    .fullName(StringUtils.hasText(request.adminFullName()) ? request.adminFullName() : "Admin")
                    .role("ACADEMY_ADMIN")
                    .build();
            userRepository.save(admin);
        }

        return toResponse(saved);
    }

    @Transactional
    public AcademyResponse update(Long id, UpdateAcademyRequest request) {
        Academy academy = findOrFail(id);

        if (request.name() != null) academy.setName(request.name());
        if (request.planType() != null) academy.setPlanType(request.planType());
        if (request.maxStudents() != null) academy.setMaxStudents(request.maxStudents());
        if (request.maxCourses() != null) academy.setMaxCourses(request.maxCourses());
        if (request.maxStorageMb() != null) academy.setMaxStorageMb(request.maxStorageMb());

        // Report schedule config (stored in JSONB)
        if (request.reportScheduleDay() != null || request.reportScheduleHour() != null) {
            Map<String, Object> config = academy.getConfig() != null ? academy.getConfig() : new HashMap<>();
            if (request.reportScheduleDay() != null) config.put("reportScheduleDay", request.reportScheduleDay());
            if (request.reportScheduleHour() != null) config.put("reportScheduleHour", request.reportScheduleHour());
            academy.setConfig(config);
        }

        return toResponse(academyRepository.save(academy));
    }

    @Transactional
    @CacheEvict(value = "academy-features", key = "#id")
    public AcademyResponse updateFeatures(Long id, UpdateFeaturesRequest request) {
        Academy academy = findOrFail(id);

        if (request.features() != null) {
            request.features().keySet().forEach(Feature::fromKey);

            Map<String, Boolean> currentFeatures = academy.getFeatures();
            if (currentFeatures == null) {
                currentFeatures = new HashMap<>();
            }
            currentFeatures.putAll(request.features());
            academy.setFeatures(new HashMap<>(currentFeatures));
        }

        return toResponse(academyRepository.save(academy));
    }

    @Transactional
    public AcademyResponse activate(Long id) {
        Academy academy = findOrFail(id);
        academy.setIsActive(true);
        return toResponse(academyRepository.save(academy));
    }

    @Transactional
    public AcademyResponse deactivate(Long id) {
        Academy academy = findOrFail(id);
        academy.setIsActive(false);
        return toResponse(academyRepository.save(academy));
    }

    // --- Subscriptions ---

    @Transactional
    public SubscriptionResponse registerPayment(Long academyId, CreateSubscriptionRequest request, Long createdBy) {
        Academy academy = findOrFail(academyId);

        AcademySubscription subscription = AcademySubscription.builder()
                .academyId(academyId)
                .planType(request.planType())
                .amountPaid(request.amountPaid())
                .currency(request.currency() != null ? request.currency() : "PEN")
                .periodStart(request.periodStart().atStartOfDay())
                .periodEnd(request.periodEnd().atTime(23, 59, 59))
                .paymentMethod(request.paymentMethod())
                .notes(request.notes())
                .createdBy(createdBy)
                .build();

        subscriptionRepository.save(subscription);

        // Actualizar academia con nuevo periodo y reactivar
        academy.setPlanType(request.planType());
        academy.setSubscriptionStart(request.periodStart().atStartOfDay());
        academy.setSubscriptionEnd(request.periodEnd().atTime(23, 59, 59));
        academy.setIsActive(true);
        academyRepository.save(academy);

        return toSubscriptionResponse(subscription);
    }

    public List<SubscriptionResponse> getPaymentHistory(Long academyId) {
        findOrFail(academyId); // validar que existe
        return subscriptionRepository.findByAcademyIdOrderByCreatedAtDesc(academyId)
                .stream()
                .map(this::toSubscriptionResponse)
                .toList();
    }

    // --- Helpers ---

    public Academy findOrFail(Long id) {
        return academyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada con ID: " + id));
    }

    @Cacheable(value = "academy-features", key = "#academyId")
    public Map<String, Boolean> getFeatures(Long academyId) {
        // Copy into a plain HashMap: the Redis serializer cannot round-trip
        // JDK immutable maps (Map.of) nor Hibernate-managed collections
        return academyRepository.findById(academyId)
                .<Map<String, Boolean>>map(a -> a.getFeatures() != null
                        ? new HashMap<>(a.getFeatures())
                        : new HashMap<>())
                .orElseGet(HashMap::new);
    }

    public void assertFeatureEnabled(Feature feature) {
        Long academyId = TenantContext.getCurrentAcademyId();
        if (academyId == null) return;
        Map<String, Boolean> features = self.getObject().getFeatures(academyId);
        if (!Boolean.TRUE.equals(features.get(feature.key()))) {
            throw new AccessDeniedException(
                    "La funcionalidad '" + feature.key() + "' no está habilitada para esta academia. " +
                    "Contacte al administrador del sistema.");
        }
    }

    private Map<String, Boolean> defaultFeatures() {
        Map<String, Boolean> features = new HashMap<>();
        for (Feature f : Feature.values()) {
            features.put(f.key(), false);
        }
        features.put(Feature.GRADE_REPORTS.key(), true);
        return features;
    }

    private AcademyResponse toResponse(Academy a) {
        return new AcademyResponse(
                a.getId(), a.getUuid(), a.getName(), a.getSubdomain(),
                a.getPlanType(), a.getMaxStudents(), a.getMaxCourses(),
                a.getMaxStorageMb(), a.getFeatures(), a.getIsActive(),
                a.getSubscriptionStart(), a.getSubscriptionEnd(), a.getCreatedAt()
        );
    }

    private SubscriptionResponse toSubscriptionResponse(AcademySubscription s) {
        return new SubscriptionResponse(
                s.getId(), s.getAcademyId(), s.getPlanType(), s.getAmountPaid(),
                s.getCurrency(), s.getPeriodStart(), s.getPeriodEnd(),
                s.getPaymentMethod(), s.getNotes(), s.getCreatedAt()
        );
    }
}

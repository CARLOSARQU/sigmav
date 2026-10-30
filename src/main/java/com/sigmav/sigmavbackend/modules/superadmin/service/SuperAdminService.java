package com.sigmav.sigmavbackend.modules.superadmin.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.entity.AcademySubscription;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademySubscriptionRepository;
import com.sigmav.sigmavbackend.modules.superadmin.dto.*;
import com.sigmav.sigmavbackend.modules.user.entity.User;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import com.sigmav.sigmavbackend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final AcademyRepository academyRepository;
    private final AcademySubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    // --- Dashboard ---

    public DashboardResponse getDashboard() {
        var allAcademies = academyRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekLater = now.plusDays(7);

        long totalAcademies = allAcademies.size();
        long activeAcademies = allAcademies.stream().filter(Academy::getIsActive).count();
        long totalStudents = userRepository.countByRole("STUDENT");

        var allSubscriptions = subscriptionRepository.findAll();

        BigDecimal monthlyRevenue = allSubscriptions.stream()
                .filter(s -> s.getCreatedAt().getMonth() == now.getMonth()
                        && s.getCreatedAt().getYear() == now.getYear())
                .map(AcademySubscription::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRevenue = allSubscriptions.stream()
                .map(AcademySubscription::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long expiringThisWeek = allAcademies.stream()
                .filter(a -> a.getIsActive()
                        && a.getSubscriptionEnd() != null
                        && a.getSubscriptionEnd().isAfter(now)
                        && a.getSubscriptionEnd().isBefore(weekLater))
                .count();

        Map<String, Long> planDistribution = allAcademies.stream()
                .collect(Collectors.groupingBy(Academy::getPlanType, Collectors.counting()));

        // Gráfico: ingresos de los últimos 6 meses
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<MonthlyRevenuePoint> monthlyRevenueChart = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime month = now.minusMonths(i);
            BigDecimal revenue = allSubscriptions.stream()
                    .filter(s -> s.getCreatedAt().getYear() == month.getYear()
                            && s.getCreatedAt().getMonth() == month.getMonth())
                    .map(AcademySubscription::getAmountPaid)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            monthlyRevenueChart.add(new MonthlyRevenuePoint(month.format(monthFmt), revenue));
        }

        // Últimas 5 academias creadas
        List<RecentAcademyItem> recentAcademies = allAcademies.stream()
                .filter(a -> a.getCreatedAt() != null)
                .sorted(Comparator.comparing(Academy::getCreatedAt).reversed())
                .limit(5)
                .map(a -> new RecentAcademyItem(
                        a.getId(), a.getUuid(), a.getName(), a.getSubdomain(),
                        a.getPlanType(), a.getIsActive(), a.getCreatedAt(), a.getSubscriptionEnd()))
                .toList();

        return new DashboardResponse(
                totalAcademies, activeAcademies, totalStudents,
                monthlyRevenue, totalRevenue, expiringThisWeek,
                planDistribution, 0L,
                monthlyRevenueChart, recentAcademies
        );
    }

    // --- Gestión global de usuarios (B3.9) ---

    public Page<UserSummaryResponse> listUsers(Long academyId, String role, Boolean isActive, String q, Pageable pageable) {
        Specification<User> spec = Specification.where((root, query, cb) -> cb.conjunction());

        if (academyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academyId"), academyId));
        }
        if (StringUtils.hasText(role)) {
            String roleStr = role.toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), roleStr));
        }
        if (isActive != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), isActive));
        }
        if (StringUtils.hasText(q)) {
            String pattern = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            ));
        }

        Page<User> usersPage = userRepository.findAll(spec, pageable);

        // Carga los nombres de academia en una sola query para evitar N+1
        Set<Long> academyIds = usersPage.stream()
                .map(User::getAcademyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> academyNames = buildAcademyNameMap(academyIds);

        return usersPage.map(u -> toUserSummaryResponse(u, academyNames.get(u.getAcademyId())));
    }

    public UserSummaryResponse getUserById(Long id) {
        User user = findUserOrFail(id);
        return toUserSummaryResponse(user, resolveAcademyName(user.getAcademyId()));
    }

    @Transactional
    public UserSummaryResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUserOrFail(id);

        if (request.role() != null) {
            user.setRole(request.role().toUpperCase());
        }
        if (request.isActive() != null) {
            user.setIsActive(request.isActive());
        }
        if (request.academyId() != null) {
            if (!academyRepository.existsById(request.academyId())) {
                throw new NotFoundException("La academia con ID " + request.academyId() + " no existe.");
            }
            user.setAcademyId(request.academyId());
        }

        User saved = userRepository.save(user);

        // The cached UserPrincipal carries role, isActive and academyId; all three
        // are mutable here. Evict unconditionally so a demotion, deactivation or
        // tenant move takes effect on the next request instead of surviving up to
        // the 5-minute TTL.
        //
        // Residual race: this statement runs inside the @Transactional boundary,
        // and the commit happens after the method returns. A concurrent read can
        // therefore still reload the pre-commit row and repopulate the entry
        // before the commit lands. The window collapses from the full TTL to a
        // sub-millisecond race, but it is not airtight; closing it would need a
        // post-commit eviction hook.
        cacheManager.getCache("user-principals").evict(saved.getId());

        return toUserSummaryResponse(saved, resolveAcademyName(saved.getAcademyId()));
    }

    @Transactional
    public void changeUserPassword(Long id, ChangePasswordRequest request) {
        User user = findUserOrFail(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // --- Usuarios por academia ---

    public Page<UserSummaryResponse> listAcademyUsers(Long academyId, Pageable pageable) {
        Academy academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada con ID: " + academyId));
        String academyName = academy.getName();

        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("academyId"), academyId);
        return userRepository.findAll(spec, pageable)
                .map(u -> toUserSummaryResponse(u, academyName));
    }

    @Transactional
    public UserSummaryResponse createAcademyUser(Long academyId, CreateAcademyUserRequest request) {
        Academy academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new NotFoundException("Academia no encontrada con ID: " + academyId));

        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmailAndAcademyId(email, academyId)) {
            throw new IllegalArgumentException("Ya existe un usuario con ese email en la academia");
        }

        String role = "ACADEMY_ADMIN";
        if (StringUtils.hasText(request.role())) {
            role = request.role().toUpperCase();
            if (!"ACADEMY_ADMIN".equals(role) && !"TEACHER".equals(role)) {
                throw new IllegalArgumentException("Rol inválido: " + request.role() + ". Roles permitidos: ACADEMY_ADMIN, TEACHER");
            }
        }

        User user = User.builder()
                .academyId(academyId)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(role)
                .build();

        return toUserSummaryResponse(userRepository.save(user), academy.getName());
    }

    // --- Helpers ---

    private User findUserOrFail(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado con ID: " + id));
    }

    private String resolveAcademyName(Long academyId) {
        if (academyId == null) return null;
        return academyRepository.findById(academyId).map(Academy::getName).orElse(null);
    }

    private Map<Long, String> buildAcademyNameMap(Set<Long> academyIds) {
        if (academyIds.isEmpty()) return Map.of();
        return academyRepository.findAllById(academyIds).stream()
                .collect(Collectors.toMap(Academy::getId, Academy::getName));
    }

    private UserSummaryResponse toUserSummaryResponse(User u, String academyName) {
        return new UserSummaryResponse(
                u.getId(), u.getUuid(), u.getAcademyId(), academyName,
                u.getEmail(), u.getFullName(), u.getRole(),
                u.getIsActive(), u.getCreatedAt()
        );
    }
}

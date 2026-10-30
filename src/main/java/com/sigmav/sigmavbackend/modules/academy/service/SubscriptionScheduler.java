package com.sigmav.sigmavbackend.modules.academy.service;

import com.sigmav.sigmavbackend.modules.academy.entity.Academy;
import com.sigmav.sigmavbackend.modules.academy.repository.AcademyRepository;
import com.sigmav.sigmavbackend.modules.notification.service.NotificationService;
import com.sigmav.sigmavbackend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionScheduler {

    private final AcademyRepository academyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Cada hora: desactivar academias con suscripción vencida
     */
    @Scheduled(fixedRate = 3600000) // cada hora
    @Transactional
    public void deactivateExpiredAcademies() {
        LocalDateTime now = LocalDateTime.now();
        List<Academy> expired = academyRepository
                .findByIsActiveTrueAndSubscriptionEndBefore(now);

        for (Academy academy : expired) {
            academy.setIsActive(false);
            academyRepository.save(academy);

            // Notificar al owner de la academia
            userRepository.findByAcademyIdAndRole(academy.getId(), "ACADEMY_ADMIN")
                    .ifPresent(owner -> notificationService.createForUser(
                            owner.getId(),
                            academy.getId(),
                            "SUBSCRIPTION_EXPIRED",
                            "Suscripción vencida",
                            "La suscripción de " + academy.getName() + " ha vencido. La academia ha sido desactivada."
                    ));

            log.info("Academia desactivada por vencimiento: {} (ID: {})", academy.getName(), academy.getId());
        }
    }

    /**
     * Diario a las 9 am: alertar academias próximas a vencer (7 días)
     */
    @Scheduled(cron = "0 0 9 * * *") // 9:00 AM todos los días
    @Transactional
    public void alertExpiringAcademies() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekLater = now.plusDays(7);

        List<Academy> expiring = academyRepository
                .findByIsActiveTrueAndSubscriptionEndBetween(now, weekLater);

        for (Academy academy : expiring) {
            userRepository.findByAcademyIdAndRole(academy.getId(), "ACADEMY_ADMIN")
                    .ifPresent(owner -> notificationService.createForUser(
                            owner.getId(),
                            academy.getId(),
                            "SUBSCRIPTION_EXPIRING",
                            "Suscripción próxima a vencer",
                            "La suscripción de " + academy.getName() + " vence pronto. Renueve para mantener el acceso."
                    ));

            log.info("Alerta de vencimiento enviada: {} (ID: {})", academy.getName(), academy.getId());
        }
    }
}

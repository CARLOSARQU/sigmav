package com.sigmav.sigmavbackend.modules.notification.service;

import com.sigmav.sigmavbackend.modules.notification.entity.Notification;
import com.sigmav.sigmavbackend.modules.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void createForUser(Long userId, Long academyId, String type, String title, String message) {
        Notification notification = Notification.builder()
                .targetType("USER")
                .userId(userId)
                .academyId(academyId)   // almacenamos academyId incluso en notifs USER para poder filtrar
                .type(type)
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional
    public void createBatchForUsers(java.util.List<Long> userIds, Long academyId,
                                    String type, String title, String message) {
        if (userIds == null || userIds.isEmpty()) return;
        java.util.List<Notification> notifications = userIds.stream()
                .map(uid -> Notification.builder()
                        .targetType("USER")
                        .userId(uid)
                        .academyId(academyId)
                        .type(type)
                        .title(title)
                        .message(message)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
    }

    public void createForAcademy(Long academyId, String type, String title, String message) {
        Notification notification = Notification.builder()
                .targetType("ACADEMY")
                .academyId(academyId)
                .type(type)
                .title(title)
                .message(message)
                .build();
        notificationRepository.save(notification);
    }

    public Page<Notification> getByUserId(Long userId, Long academyId, Pageable pageable) {
        return notificationRepository.findByUserIdAndAcademyIdOrderByCreatedAtDesc(userId, academyId, pageable);
    }

    public long countUnread(Long userId, Long academyId) {
        return notificationRepository.countByUserIdAndAcademyIdAndIsReadFalse(userId, academyId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long requestingUserId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            // Solo el destinatario puede marcar su notificación como leída
            if (n.getUserId() != null && !n.getUserId().equals(requestingUserId)) {
                return;
            }
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }
}

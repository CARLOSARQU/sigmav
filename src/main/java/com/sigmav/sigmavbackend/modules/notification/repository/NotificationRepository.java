package com.sigmav.sigmavbackend.modules.notification.repository;

import com.sigmav.sigmavbackend.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findByAcademyIdOrderByCreatedAtDesc(Long academyId, Pageable pageable);

    /** Con filtro de academia — evita que notifs de otra academia aparezcan por colisión de userId */
    Page<Notification> findByUserIdAndAcademyIdOrderByCreatedAtDesc(Long userId, Long academyId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    long countByUserIdAndAcademyIdAndIsReadFalse(Long userId, Long academyId);
}

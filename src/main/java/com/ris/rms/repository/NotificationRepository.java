package com.ris.rms.repository;

import com.ris.rms.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Notification> findAllByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    void deleteByNotificationIdAndUserId(Long notificationId, Long userId);
}

package com.ris.rms.service;

import com.ris.rms.entity.Notification;
import org.springframework.data.domain.Page;

public interface NotificationService {

    Page<Notification> list(Long userId, Boolean isRead, Integer page, Integer size);

    long unreadCount(Long userId);

    void markRead(Long userId, Long notificationId, boolean isRead);

    int markAllRead(Long userId);

    void deleteOne(Long userId, Long notificationId);
}

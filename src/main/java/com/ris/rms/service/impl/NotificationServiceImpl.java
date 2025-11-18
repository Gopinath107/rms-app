package com.ris.rms.service.impl;

import com.ris.rms.entity.Notification;
import com.ris.rms.repository.NotificationRepository;
import com.ris.rms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

	private final NotificationRepository repo;

	@Override
	@Transactional(readOnly = true)
	public Page<Notification> list(Long userId, Boolean isRead, Integer page, Integer size) {
		if (userId == null)
			throw new IllegalArgumentException("userId is required");
		int p = (page == null || page < 0) ? 0 : page;
		int s = (size == null || size <= 0) ? 20 : size;

		if (isRead == null) {
			return repo.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(p, s));
		}
		return repo.findAllByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, PageRequest.of(p, s));
	}

	@Override
	@Transactional(readOnly = true)
	public long unreadCount(Long userId) {
		if (userId == null)
			throw new IllegalArgumentException("userId is required");
		return repo.countByUserIdAndIsReadFalse(userId);
	}

	@Override
	public void markRead(Long userId, Long notificationId, boolean isRead) {
		if (userId == null)
			throw new IllegalArgumentException("userId is required");
		if (notificationId == null)
			throw new IllegalArgumentException("notificationId is required");

		if (!repo.existsByNotificationIdAndUserId(notificationId, userId)) {
			throw new IllegalArgumentException("Notification not found for this user");
		}
		Notification n = repo.findById(notificationId)
				.orElseThrow(() -> new IllegalArgumentException("Notification not found"));
		n.setIsRead(isRead);
		repo.save(n);
	}

	@Override
	public int markAllRead(Long userId) {
		if (userId == null)
			throw new IllegalArgumentException("userId is required");

		var page = repo.findAllByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, PageRequest.of(0, 500));
		int total = 0;
		while (true) {
			for (Notification n : page.getContent()) {
				n.setIsRead(true);
			}
			repo.saveAll(page.getContent());
			total += page.getNumberOfElements();
			if (!page.hasNext())
				break;
			page = repo.findAllByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, page.nextPageable());
		}
		return total;
	}

	@Override
	public void deleteOne(Long userId, Long notificationId) {
		if (userId == null)
			throw new IllegalArgumentException("userId is required");
		if (notificationId == null)
			throw new IllegalArgumentException("notificationId is required");
		if (!repo.existsByNotificationIdAndUserId(notificationId, userId)) {
			throw new IllegalArgumentException("Notification not found for this user");
		}
		repo.deleteByNotificationIdAndUserId(notificationId, userId);
	}
}

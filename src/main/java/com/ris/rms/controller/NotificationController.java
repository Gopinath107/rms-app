package com.ris.rms.controller;

import com.ris.rms.entity.Notification;
import com.ris.rms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService service;

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam Long userId,
			@RequestParam(required = false) Boolean isRead, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Page<Notification> p = service.list(userId, isRead, page, size);

			resp.put("result", p.getContent());
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@GetMapping("/unread-count")
	public ResponseEntity<Map<String, Object>> unreadCount(@RequestParam Long userId) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			long count = service.unreadCount(userId);
			resp.put("result", Map.of("unreadCount", count));
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", Map.of("unreadCount", 0));
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/MarkRead")
	public ResponseEntity<Map<String, Object>> markRead(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long userId = asLong(body.get("userId"));
			Long notificationId = asLong(body.get("notificationId"));
			Boolean isRead = asBoolean(body.get("isRead"));
			if (isRead == null)
				throw new IllegalArgumentException("isRead is required");

			service.markRead(userId, notificationId, isRead);

			resp.put("result", Map.of("notificationId", notificationId, "isRead", isRead));
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/MarkAllRead")
	public ResponseEntity<Map<String, Object>> markAllRead(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long userId = asLong(body.get("userId"));
			int updated = service.markAllRead(userId);
			resp.put("result", Map.of("updated", updated));
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	private static String cleanMsg(Exception e) {
		String m = e.getMessage();
		return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
	}

	private static Long asLong(Object o) {
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.longValue();
		try {
			return Long.parseLong(o.toString());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Boolean asBoolean(Object o) {
		if (o == null)
			return null;
		if (o instanceof Boolean b)
			return b;
		return "true".equalsIgnoreCase(o.toString()) || "1".equals(o.toString());
	}
}

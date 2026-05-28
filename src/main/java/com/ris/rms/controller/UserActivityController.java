package com.ris.rms.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.dto.DashboardStatsDto;
import com.ris.rms.dto.ModuleUsageDto;
import com.ris.rms.dto.OverviewStatsDto;
import com.ris.rms.dto.RealtimeActivityDto;
import com.ris.rms.dto.ScreenUsageDto;
import com.ris.rms.dto.SessionTimelineDto;
import com.ris.rms.dto.UserActivityDto;
import com.ris.rms.dto.UserActivitySummaryDto;
import com.ris.rms.service.UserActivityService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user-activity")
public class UserActivityController {

	private final UserActivityService service;

	// ─── POST /track ────────────────────────────────────────────────────
	@PostMapping("/track")
	public ResponseEntity<Map<String, Object>> trackEvent(@RequestBody UserActivityDto dto,
			HttpServletRequest request) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			dto.setIpAddress(resolveClientIp(request));
			dto.setUserAgent(request.getHeader("User-Agent"));

			service.recordEvent(dto);

			resp.put("result", "Event recorded");
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);

		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── POST /track-batch ──────────────────────────────────────────────
	@PostMapping("/track-batch")
	public ResponseEntity<Map<String, Object>> trackBatchEvents(@RequestBody List<UserActivityDto> events,
			HttpServletRequest request) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			String clientIp = resolveClientIp(request);
			String userAgent = request.getHeader("User-Agent");

			for (UserActivityDto dto : events) {
				dto.setIpAddress(clientIp);
				dto.setUserAgent(userAgent);
			}

			service.recordBatchEvents(events);

			resp.put("result", "Batch of " + events.size() + " events recorded");
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);

		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /summary ───────────────────────────────────────────────────
	@GetMapping("/summary")
	public ResponseEntity<Map<String, Object>> getSummary(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String status) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<UserActivitySummaryDto> result = service.getActivitySummary(userId, from, to, module, status);

			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);

		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /dashboard-stats ───────────────────────────────────────────
	@GetMapping("/dashboard-stats")
	public ResponseEntity<Map<String, Object>> getDashboardStats(
			@RequestParam(required = false) LocalDate date) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			DashboardStatsDto stats = service.getDashboardStats(date);

			resp.put("result", stats);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);

		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /detail/{userId} ───────────────────────────────────────────
	@GetMapping("/detail/{userId}")
	public ResponseEntity<Map<String, Object>> getUserSessionDetail(
			@PathVariable Long userId,
			@RequestParam(required = false) String sessionId) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<UserActivitySummaryDto.ScreenDetailDto> detail = service.getUserSessionDetail(userId, sessionId);

			resp.put("result", detail);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);

		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ═══════════════════════════════════════════════════════════════════
	// ── ANALYTICS ENDPOINTS ────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════

	// ─── GET /realtime ──────────────────────────────────────────────────
	@GetMapping("/realtime")
	public ResponseEntity<Map<String, Object>> getRealtime() {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			RealtimeActivityDto result = service.getRealtimeActivity();
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /overview ──────────────────────────────────────────────────
	@GetMapping("/overview")
	public ResponseEntity<Map<String, Object>> getOverview(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(defaultValue = "hourly") String granularity) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			OverviewStatsDto result = service.getOverviewStats(from, to, granularity);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /module-usage ──────────────────────────────────────────────
	@GetMapping("/module-usage")
	public ResponseEntity<Map<String, Object>> getModuleUsage(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<ModuleUsageDto> result = service.getModuleUsage(from, to);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /screen-usage ──────────────────────────────────────────────
	@GetMapping("/screen-usage")
	public ResponseEntity<Map<String, Object>> getScreenUsage(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String module) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<ScreenUsageDto> result = service.getScreenUsage(from, to, module);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /sessions ──────────────────────────────────────────────────
	@GetMapping("/sessions")
	public ResponseEntity<Map<String, Object>> getSessions(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String status) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<UserActivitySummaryDto> result = service.getSessionsList(userId, from, to, module, status);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /session-detail/{sessionId} ────────────────────────────────
	@GetMapping("/session-detail/{sessionId}")
	public ResponseEntity<Map<String, Object>> getSessionTimeline(
			@PathVariable String sessionId) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			SessionTimelineDto result = service.getSessionTimeline(sessionId);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	// ─── GET /export ────────────────────────────────────────────────────
	@GetMapping("/export")
	public ResponseEntity<byte[]> exportCsv(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String module,
			@RequestParam(required = false) String status) {

		try {
			List<UserActivitySummaryDto> data = service.exportSessions(userId, from, to, module, status);

			StringBuilder csv = new StringBuilder();
			csv.append("User,User ID,Session ID,Login Time,Logout Time,Duration (min),Module,Screen,Pages Visited,Status,IP,Device,Browser\n");

			for (UserActivitySummaryDto row : data) {
				csv.append(escapeCsv(row.getUserName())).append(',');
				csv.append(row.getUserId()).append(',');
				csv.append(escapeCsv(row.getSessionId())).append(',');
				csv.append(escapeCsv(row.getLoginTime())).append(',');
				csv.append(escapeCsv(row.getLogoutTime())).append(',');
				csv.append(row.getDurationMinutes()).append(',');
				csv.append(escapeCsv(row.getCurrentModule())).append(',');
				csv.append(escapeCsv(row.getCurrentScreen())).append(',');
				csv.append(row.getPagesVisited()).append(',');
				csv.append(escapeCsv(row.getStatus())).append(',');
				csv.append(escapeCsv(row.getIpAddress())).append(',');
				csv.append(escapeCsv(row.getDeviceType())).append(',');
				csv.append(escapeCsv(row.getBrowserName())).append('\n');
			}

			byte[] csvBytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-activity-export.csv")
					.contentType(MediaType.parseMediaType("text/csv"))
					.contentLength(csvBytes.length)
					.body(csvBytes);

		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	// ─── Helper: Resolve client IP ──────────────────────────────────────
	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	// ─── Helper: Escape CSV field ───────────────────────────────────────
	private String escapeCsv(String value) {
		if (value == null) return "";
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}

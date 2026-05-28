package com.ris.rms.service.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ris.rms.dto.DashboardStatsDto;
import com.ris.rms.dto.ModuleUsageDto;
import com.ris.rms.dto.OverviewStatsDto;
import com.ris.rms.dto.RealtimeActivityDto;
import com.ris.rms.dto.ScreenUsageDto;
import com.ris.rms.dto.SessionTimelineDto;
import com.ris.rms.dto.UserActivityDto;
import com.ris.rms.dto.UserActivitySummaryDto;
import com.ris.rms.entity.UserActivity;
import com.ris.rms.repository.UserActivityRepository;
import com.ris.rms.service.UserActivityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

	private final UserActivityRepository repository;

	private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	// ─── Record a single event ──────────────────────────────────────────
	@Override
	public void recordEvent(UserActivityDto dto) {
		repository.save(toEntity(dto));
	}

	// ─── Record a batch of events ───────────────────────────────────────
	@Override
	public void recordBatchEvents(List<UserActivityDto> events) {
		List<UserActivity> entities = events.stream().map(this::toEntity).toList();
		repository.saveAll(entities);
	}

	// ─── Activity Summary (grouped by session) ──────────────────────────
	@Override
	public List<UserActivitySummaryDto> getActivitySummary(Long userId, LocalDate from, LocalDate to,
			String module, String status) {

		OffsetDateTime start = (from != null ? from : LocalDate.now()).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime end = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
				.atStartOfDay().atOffset(ZoneOffset.UTC);

		List<UserActivity> events;
		if (userId != null) {
			events = repository.findByUserIdAndEventTimeBetween(userId, start, end);
		} else {
			events = repository.findByEventTimeBetweenOrderByEventTimeDesc(start, end);
		}

		List<UserActivitySummaryDto> summaries = buildSessionSummaries(events);

		// Filter by module if provided
		if (module != null && !module.isBlank()) {
			summaries = summaries.stream()
					.filter(s -> module.equalsIgnoreCase(s.getCurrentModule()))
					.collect(Collectors.toList());
		}

		// Filter by status if provided
		if (status != null && !status.isBlank()) {
			summaries = summaries.stream()
					.filter(s -> status.equalsIgnoreCase(s.getStatus()))
					.collect(Collectors.toList());
		}

		// Sort by lastActiveTime descending (null-safe to avoid NPE)
		summaries.sort(Comparator.comparing(UserActivitySummaryDto::getLastActiveTime,
				Comparator.nullsLast(Comparator.naturalOrder())).reversed());

		return summaries;
	}

	// ─── Dashboard Stats ────────────────────────────────────────────────
	@Override
	public DashboardStatsDto getDashboardStats(LocalDate date) {
		LocalDate targetDate = date != null ? date : LocalDate.now();
		OffsetDateTime dayStart = targetDate.atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime dayEnd = targetDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		List<UserActivity> dayEvents = repository.findByEventTimeBetweenOrderByEventTimeDesc(dayStart, dayEnd);

		int totalActiveUsersToday = (int) dayEvents.stream()
				.map(UserActivity::getUserId)
				.distinct()
				.count();

		Map<String, List<UserActivity>> bySession = dayEvents.stream()
				.collect(Collectors.groupingBy(UserActivity::getSessionId));

		int totalLoggedInUsers = 0;
		List<Long> sessionDurations = new ArrayList<>();

		for (var entry : bySession.entrySet()) {
			List<UserActivity> sessionEvents = entry.getValue().stream()
					.sorted(Comparator.comparing(UserActivity::getEventTime))
					.toList();

			boolean hasLogin = sessionEvents.stream().anyMatch(e -> "LOGIN".equals(e.getEventType()));
			boolean hasLogout = sessionEvents.stream().anyMatch(e -> "LOGOUT".equals(e.getEventType()));
			UserActivity lastEvent = sessionEvents.getLast();

			if (hasLogin && !hasLogout && Duration.between(lastEvent.getEventTime(), now).toMinutes() <= 30) {
				totalLoggedInUsers++;
			}

			if (hasLogin) {
				OffsetDateTime loginT = sessionEvents.stream()
						.filter(e -> "LOGIN".equals(e.getEventType()))
						.map(UserActivity::getEventTime)
						.min(Comparator.naturalOrder()).orElse(null);
				if (loginT != null) {
					OffsetDateTime endT = hasLogout
							? sessionEvents.stream()
									.filter(e -> "LOGOUT".equals(e.getEventType()))
									.map(UserActivity::getEventTime)
									.max(Comparator.naturalOrder()).orElse(lastEvent.getEventTime())
							: lastEvent.getEventTime();
					sessionDurations.add(Duration.between(loginT, endT).toMinutes());
				}
			}
		}

		double averageUsageMinutes = sessionDurations.isEmpty() ? 0.0
				: sessionDurations.stream().mapToLong(Long::longValue).average().orElse(0.0);
		averageUsageMinutes = Math.round(averageUsageMinutes * 100.0) / 100.0;

		Map<String, Long> moduleCounts = dayEvents.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()) && e.getModuleName() != null)
				.collect(Collectors.groupingBy(UserActivity::getModuleName, Collectors.counting()));

		String mostUsedModule = null;
		int mostUsedModuleCount = 0;
		if (!moduleCounts.isEmpty()) {
			var topEntry = moduleCounts.entrySet().stream()
					.max(Map.Entry.comparingByValue())
					.orElse(null);
			if (topEntry != null) {
				mostUsedModule = topEntry.getKey();
				mostUsedModuleCount = topEntry.getValue().intValue();
			}
		}

		OffsetDateTime fifteenMinAgo = now.minusMinutes(15);
		int recentlyActiveUsers = (int) dayEvents.stream()
				.filter(e -> e.getEventTime().isAfter(fifteenMinAgo))
				.map(UserActivity::getUserId)
				.distinct()
				.count();

		return DashboardStatsDto.builder()
				.totalActiveUsersToday(totalActiveUsersToday)
				.totalLoggedInUsers(totalLoggedInUsers)
				.averageUsageMinutes(averageUsageMinutes)
				.mostUsedModule(mostUsedModule)
				.recentlyActiveUsers(recentlyActiveUsers)
				.mostUsedModuleCount(mostUsedModuleCount)
				.build();
	}

	// ─── User Session Detail ────────────────────────────────────────────
	@Override
	public List<UserActivitySummaryDto.ScreenDetailDto> getUserSessionDetail(Long userId, String sessionId) {
		List<UserActivity> sessionEvents = repository.findBySessionIdOrderByEventTimeAsc(sessionId);

		sessionEvents = sessionEvents.stream()
				.filter(e -> e.getUserId().equals(userId))
				.toList();

		List<UserActivity> pageViews = sessionEvents.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
				.toList();

		return buildScreenDetails(pageViews.isEmpty() ? sessionEvents : pageViews.stream()
				.sorted(Comparator.comparing(UserActivity::getEventTime)).toList());
	}

	// ═══════════════════════════════════════════════════════════════════
	// ── ANALYTICS APIs ─────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════

	// ─── GET /realtime ──────────────────────────────────────────────────
	@Override
	public RealtimeActivityDto getRealtimeActivity() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime thirtyMinAgo = now.minusMinutes(30);

		List<UserActivity> recentEvents = repository.findByEventTimeBetweenOrderByEventTimeDesc(thirtyMinAgo, now);

		Map<String, List<UserActivity>> bySession = recentEvents.stream()
				.collect(Collectors.groupingBy(UserActivity::getSessionId, LinkedHashMap::new, Collectors.toList()));

		List<RealtimeActivityDto.ActiveSession> rawSessions = new ArrayList<>();

		for (var entry : bySession.entrySet()) {
			List<UserActivity> sessionEvents = entry.getValue().stream()
					.sorted(Comparator.comparing(UserActivity::getEventTime))
					.toList();

			// Skip sessions with LOGOUT
			boolean hasLogout = sessionEvents.stream().anyMatch(e -> "LOGOUT".equals(e.getEventType()));
			if (hasLogout) continue;

			UserActivity lastEvent = sessionEvents.getLast();
			long minutesSinceLast = Duration.between(lastEvent.getEventTime(), now).toMinutes();
			if (minutesSinceLast > 30) continue;

			String sessionStatus = minutesSinceLast <= 5 ? "Active" : "Inactive";

			// Find LOGIN event for duration
			UserActivity loginEvent = sessionEvents.stream()
					.filter(e -> "LOGIN".equals(e.getEventType()))
					.findFirst().orElse(sessionEvents.getFirst());
			long durationMinutes = Duration.between(loginEvent.getEventTime(), now).toMinutes();

			// Latest PAGE_VIEW
			UserActivity latestPageView = sessionEvents.stream()
					.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
					.reduce((a, b) -> b).orElse(null);

			String ipAddress = sessionEvents.stream()
					.map(UserActivity::getIpAddress)
					.filter(ip -> ip != null && !ip.isBlank())
					.findFirst().orElse(null);
			String userAgent = sessionEvents.stream()
					.map(UserActivity::getUserAgent)
					.filter(ua -> ua != null && !ua.isBlank())
					.findFirst().orElse(null);

			String[] parsed = parseUserAgent(userAgent);

			String currentScreen = latestPageView != null ? latestPageView.getScreenName() : "Dashboard";
			String currentModule = latestPageView != null ? latestPageView.getModuleName() : "Portal";
			if (currentModule == null || currentModule.isBlank() || "Unknown".equalsIgnoreCase(currentModule)) {
				currentModule = "Portal";
			}

			rawSessions.add(RealtimeActivityDto.ActiveSession.builder()
					.userId(loginEvent.getUserId())
					.userName(loginEvent.getUserName())
					.sessionId(entry.getKey())
					.currentScreen(currentScreen)
					.currentModule(currentModule)
					.lastActiveTime(lastEvent.getEventTime().format(ISO_FMT))
					.sessionDurationMinutes(durationMinutes)
					.deviceType(parsed[1])
					.browserName(parsed[0])
					.ipAddress(ipAddress)
					.status(sessionStatus)
					.build());
		}

		// De-duplicate active sessions by userId (keep the most recently active session)
		Map<Long, RealtimeActivityDto.ActiveSession> deduplicatedMap = new LinkedHashMap<>();
		for (RealtimeActivityDto.ActiveSession session : rawSessions) {
			RealtimeActivityDto.ActiveSession existing = deduplicatedMap.get(session.getUserId());
			if (existing == null) {
				deduplicatedMap.put(session.getUserId(), session);
			} else {
				OffsetDateTime currentActive = OffsetDateTime.parse(session.getLastActiveTime());
				OffsetDateTime existingActive = OffsetDateTime.parse(existing.getLastActiveTime());
				if (currentActive.isAfter(existingActive)) {
					deduplicatedMap.put(session.getUserId(), session);
				}
			}
		}

		List<RealtimeActivityDto.ActiveSession> activeSessions = new ArrayList<>(deduplicatedMap.values());
		
		// Sort active sessions by lastActiveTime descending
		activeSessions.sort((a, b) -> OffsetDateTime.parse(b.getLastActiveTime()).compareTo(OffsetDateTime.parse(a.getLastActiveTime())));

		int activeCount = (int) activeSessions.stream()
				.filter(s -> "Active".equalsIgnoreCase(s.getStatus()))
				.map(RealtimeActivityDto.ActiveSession::getUserId)
				.distinct()
				.count();

		return RealtimeActivityDto.builder()
				.activeUsersCount(activeCount)
				.activeSessions(activeSessions)
				.build();
	}

	// ─── GET /overview ──────────────────────────────────────────────────
	@Override
	public OverviewStatsDto getOverviewStats(LocalDate from, LocalDate to, String granularity) {
		OffsetDateTime start = (from != null ? from : LocalDate.now()).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime end = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
				.atStartOfDay().atOffset(ZoneOffset.UTC);

		// Aggregate counts
		int totalUsers = repository.countDistinctUsers(start, end).intValue();
		int totalSessions = repository.countDistinctSessions(start, end).intValue();
		int totalScreenViews = repository.countScreenViews(start, end).intValue();

		// Fetch all events for trend + breakdowns
		List<UserActivity> allEvents = repository.findByEventTimeBetweenOrderByEventTimeAsc(start, end);

		// Active users (sessions with last event within 5 min)
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		Map<String, List<UserActivity>> bySession = allEvents.stream()
				.collect(Collectors.groupingBy(UserActivity::getSessionId));

		java.util.Set<Long> activeUserIds = new java.util.HashSet<>();
		List<Long> sessionDurations = new ArrayList<>();

		for (var sessionEvents : bySession.values()) {
			List<UserActivity> sorted = sessionEvents.stream()
					.sorted(Comparator.comparing(UserActivity::getEventTime)).toList();

			boolean hasLogin = sorted.stream().anyMatch(e -> "LOGIN".equals(e.getEventType()));
			boolean hasLogout = sorted.stream().anyMatch(e -> "LOGOUT".equals(e.getEventType()));
			UserActivity last = sorted.getLast();

			if (hasLogin && !hasLogout && Duration.between(last.getEventTime(), now).toMinutes() <= 5) {
				activeUserIds.add(last.getUserId());
			}

			if (hasLogin) {
				OffsetDateTime loginT = sorted.stream()
						.filter(e -> "LOGIN".equals(e.getEventType()))
						.map(UserActivity::getEventTime)
						.min(Comparator.naturalOrder()).orElse(sorted.getFirst().getEventTime());
				OffsetDateTime endT = hasLogout
						? sorted.stream().filter(e -> "LOGOUT".equals(e.getEventType()))
								.map(UserActivity::getEventTime).max(Comparator.naturalOrder()).orElse(last.getEventTime())
						: last.getEventTime();
				sessionDurations.add(Duration.between(loginT, endT).toMinutes());
			}
		}
		int activeUsers = activeUserIds.size();

		double avgDuration = sessionDurations.isEmpty() ? 0.0
				: Math.round(sessionDurations.stream().mapToLong(Long::longValue).average().orElse(0.0) * 100.0) / 100.0;

		// Module breakdown (map null/empty/Unknown to Portal)
		Map<String, List<UserActivity>> byModule = allEvents.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
				.collect(Collectors.groupingBy(e -> {
					String m = e.getModuleName();
					return (m == null || m.isBlank() || "Unknown".equalsIgnoreCase(m)) ? "Portal" : m;
				}));

		List<OverviewStatsDto.ModuleStat> moduleBreakdown = byModule.entrySet().stream()
				.map(e -> OverviewStatsDto.ModuleStat.builder()
						.moduleName(e.getKey())
						.views(e.getValue().size())
						.users((int) e.getValue().stream().map(UserActivity::getUserId).distinct().count())
						.build())
				.sorted(Comparator.comparingInt(OverviewStatsDto.ModuleStat::getViews).reversed())
				.toList();

		String mostUsedModule = moduleBreakdown.isEmpty() ? "-" : moduleBreakdown.getFirst().getModuleName();

		// Top screens
		Map<String, List<UserActivity>> byScreen = allEvents.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()) && e.getScreenName() != null)
				.collect(Collectors.groupingBy(UserActivity::getScreenName));

		List<OverviewStatsDto.ScreenStat> topScreens = byScreen.entrySet().stream()
				.map(e -> {
					String modName = e.getValue().stream().map(UserActivity::getModuleName)
							.filter(m -> m != null && !m.isBlank() && !"Unknown".equalsIgnoreCase(m))
							.findFirst().orElse("Portal");
					return OverviewStatsDto.ScreenStat.builder()
							.screenName(e.getKey())
							.moduleName(modName)
							.views(e.getValue().size())
							.uniqueUsers((int) e.getValue().stream().map(UserActivity::getUserId).distinct().count())
							.build();
				})
				.sorted(Comparator.comparingInt(OverviewStatsDto.ScreenStat::getViews).reversed())
				.limit(10)
				.toList();

		// Activity trend
		List<OverviewStatsDto.TrendPoint> trend = buildActivityTrend(allEvents, start, end, granularity);

		return OverviewStatsDto.builder()
				.totalUsers(totalUsers)
				.activeUsers(activeUsers)
				.totalSessions(totalSessions)
				.averageSessionDuration(avgDuration)
				.totalScreenViews(totalScreenViews)
				.mostUsedModule(mostUsedModule)
				.activityTrend(trend)
				.moduleBreakdown(moduleBreakdown)
				.topScreens(topScreens)
				.build();
	}

	// ─── GET /module-usage ──────────────────────────────────────────────
	@Override
	public List<ModuleUsageDto> getModuleUsage(LocalDate from, LocalDate to) {
		OffsetDateTime start = (from != null ? from : LocalDate.now()).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime end = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
				.atStartOfDay().atOffset(ZoneOffset.UTC);

		List<UserActivity> pageViews = repository.findByEventTimeBetweenOrderByEventTimeAsc(start, end).stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
				.toList();

		Map<String, List<UserActivity>> byModule = pageViews.stream()
				.collect(Collectors.groupingBy(e -> {
					String m = e.getModuleName();
					return (m == null || m.isBlank() || "Unknown".equalsIgnoreCase(m)) ? "Portal" : m;
				}, LinkedHashMap::new, Collectors.toList()));

		List<ModuleUsageDto> result = new ArrayList<>();

		for (var entry : byModule.entrySet()) {
			List<UserActivity> events = entry.getValue();
			int totalUsers = (int) events.stream().map(UserActivity::getUserId).distinct().count();
			int totalSessions = (int) events.stream().map(UserActivity::getSessionId).distinct().count();
			int totalViews = events.size();

			// Duration: sum time spent on each page (diff to next event in same session)
			long totalDurationMin = computeTotalDuration(events);
			double avgDuration = totalSessions > 0 ? Math.round((double) totalDurationMin / totalSessions * 100.0) / 100.0 : 0;

			String lastUsed = events.stream()
					.map(UserActivity::getEventTime)
					.max(Comparator.naturalOrder())
					.map(t -> t.format(ISO_FMT))
					.orElse(null);

			result.add(ModuleUsageDto.builder()
					.moduleName(entry.getKey())
					.totalUsers(totalUsers)
					.totalSessions(totalSessions)
					.totalViews(totalViews)
					.totalDurationMinutes(totalDurationMin)
					.averageDurationMinutes(avgDuration)
					.lastUsed(lastUsed)
					.build());
		}

		result.sort(Comparator.comparingInt(ModuleUsageDto::getTotalViews).reversed());
		return result;
	}

	// ─── GET /screen-usage ──────────────────────────────────────────────
	@Override
	public List<ScreenUsageDto> getScreenUsage(LocalDate from, LocalDate to, String module) {
		OffsetDateTime start = (from != null ? from : LocalDate.now()).atStartOfDay().atOffset(ZoneOffset.UTC);
		OffsetDateTime end = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
				.atStartOfDay().atOffset(ZoneOffset.UTC);

		List<UserActivity> pageViews = repository.findByEventTimeBetweenOrderByEventTimeAsc(start, end).stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()) && e.getScreenName() != null)
				.toList();

		if (module != null && !module.isBlank()) {
			pageViews = pageViews.stream()
					.filter(e -> {
						String m = e.getModuleName();
						String resolved = (m == null || m.isBlank() || "Unknown".equalsIgnoreCase(m)) ? "Portal" : m;
						return module.equalsIgnoreCase(resolved);
					})
					.toList();
		}

		Map<String, List<UserActivity>> byScreen = pageViews.stream()
				.collect(Collectors.groupingBy(UserActivity::getScreenName, LinkedHashMap::new, Collectors.toList()));

		List<ScreenUsageDto> result = new ArrayList<>();

		for (var entry : byScreen.entrySet()) {
			List<UserActivity> events = entry.getValue();
			int viewCount = events.size();
			int uniqueUsers = (int) events.stream().map(UserActivity::getUserId).distinct().count();

			long totalTimeMin = computeTotalDuration(events);
			double avgTime = viewCount > 0 ? Math.round((double) totalTimeMin / viewCount * 100.0) / 100.0 : 0;

			String moduleName = events.stream().map(UserActivity::getModuleName)
					.filter(m -> m != null && !m.isBlank() && !"Unknown".equalsIgnoreCase(m))
					.findFirst().orElse("Portal");
			String lastAccessed = events.stream()
					.map(UserActivity::getEventTime).max(Comparator.naturalOrder())
					.map(t -> t.format(ISO_FMT)).orElse(null);

			result.add(ScreenUsageDto.builder()
					.screenName(entry.getKey())
					.moduleName(moduleName)
					.viewCount(viewCount)
					.uniqueUsers(uniqueUsers)
					.totalTimeSpentMinutes(totalTimeMin)
					.averageTimeSpentMinutes(avgTime)
					.lastAccessed(lastAccessed)
					.build());
		}

		result.sort(Comparator.comparingInt(ScreenUsageDto::getViewCount).reversed());
		return result;
	}

	// ─── GET /sessions ──────────────────────────────────────────────────
	@Override
	public List<UserActivitySummaryDto> getSessionsList(Long userId, LocalDate from, LocalDate to,
			String module, String status) {
		return getActivitySummary(userId, from, to, module, status);
	}

	// ─── GET /session-detail/{sessionId} ────────────────────────────────
	@Override
	public SessionTimelineDto getSessionTimeline(String sessionId) {
		List<UserActivity> sessionEvents = repository.findBySessionIdOrderByEventTimeAsc(sessionId);

		if (sessionEvents.isEmpty()) {
			return SessionTimelineDto.builder()
					.sessionId(sessionId)
					.events(List.of())
					.build();
		}

		sessionEvents = sessionEvents.stream()
				.sorted(Comparator.comparing(UserActivity::getEventTime))
				.toList();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		// Login/Logout times
		UserActivity loginEvent = sessionEvents.stream()
				.filter(e -> "LOGIN".equals(e.getEventType()))
				.findFirst().orElse(sessionEvents.getFirst());
		UserActivity logoutEvent = sessionEvents.stream()
				.filter(e -> "LOGOUT".equals(e.getEventType()))
				.reduce((a, b) -> b).orElse(null);

		OffsetDateTime loginTime = loginEvent.getEventTime();
		OffsetDateTime logoutTime = logoutEvent != null ? logoutEvent.getEventTime() : null;
		UserActivity lastEvent = sessionEvents.getLast();

		long durationMinutes = Duration.between(loginTime, logoutTime != null ? logoutTime : lastEvent.getEventTime()).toMinutes();

		String sessionStatus;
		if (logoutEvent != null) {
			sessionStatus = "Logged Out";
		} else {
			long minutesSince = Duration.between(lastEvent.getEventTime(), now).toMinutes();
			sessionStatus = minutesSince <= 5 ? "Active" : minutesSince <= 30 ? "Inactive" : "Logged Out";
		}

		int pagesVisited = (int) sessionEvents.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
				.map(UserActivity::getScreenName).filter(s -> s != null).distinct().count();

		String ipAddress = sessionEvents.stream().map(UserActivity::getIpAddress)
				.filter(ip -> ip != null && !ip.isBlank()).findFirst().orElse(null);
		String userAgent = sessionEvents.stream().map(UserActivity::getUserAgent)
				.filter(ua -> ua != null && !ua.isBlank()).findFirst().orElse(null);
		String[] parsed = parseUserAgent(userAgent);

		// Build timeline events
		List<SessionTimelineDto.TimelineEvent> events = new ArrayList<>();
		for (int i = 0; i < sessionEvents.size(); i++) {
			UserActivity current = sessionEvents.get(i);
			long timeSpent = 0;
			if (i < sessionEvents.size() - 1) {
				timeSpent = Duration.between(current.getEventTime(), sessionEvents.get(i + 1).getEventTime()).getSeconds();
			}

			events.add(SessionTimelineDto.TimelineEvent.builder()
					.eventType(current.getEventType())
					.screenName(current.getScreenName())
					.moduleName(current.getModuleName())
					.eventTime(current.getEventTime().format(ISO_FMT))
					.timeSpentSeconds(timeSpent)
					.build());
		}

		return SessionTimelineDto.builder()
				.sessionId(sessionId)
				.userId(loginEvent.getUserId())
				.userName(loginEvent.getUserName())
				.loginTime(loginTime.format(ISO_FMT))
				.logoutTime(logoutTime != null ? logoutTime.format(ISO_FMT) : null)
				.durationMinutes(durationMinutes)
				.status(sessionStatus)
				.ipAddress(ipAddress)
				.browserName(parsed[0])
				.deviceType(parsed[1])
				.pagesVisited(pagesVisited)
				.events(events)
				.build();
	}

	// ─── GET /export ────────────────────────────────────────────────────
	@Override
	public List<UserActivitySummaryDto> exportSessions(Long userId, LocalDate from, LocalDate to,
			String module, String status) {
		return getActivitySummary(userId, from, to, module, status);
	}

	// ═══════════════════════════════════════════════════════════════════
	// ── PRIVATE HELPERS ────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════

	/**
	 * Build session summaries from a list of events (shared by summary and sessions endpoints).
	 */
	private List<UserActivitySummaryDto> buildSessionSummaries(List<UserActivity> events) {
		Map<String, List<UserActivity>> bySession = events.stream()
				.collect(Collectors.groupingBy(UserActivity::getSessionId, LinkedHashMap::new, Collectors.toList()));

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		List<UserActivitySummaryDto> summaries = new ArrayList<>();

		for (var entry : bySession.entrySet()) {
			String sessionId = entry.getKey();
			List<UserActivity> sessionEvents = entry.getValue().stream()
					.sorted(Comparator.comparing(UserActivity::getEventTime))
					.toList();

			UserActivity loginEvent = sessionEvents.stream()
					.filter(e -> "LOGIN".equals(e.getEventType()))
					.findFirst().orElse(null);
			OffsetDateTime loginTime = loginEvent != null ? loginEvent.getEventTime() : sessionEvents.getFirst().getEventTime();

			UserActivity logoutEvent = sessionEvents.stream()
					.filter(e -> "LOGOUT".equals(e.getEventType()))
					.reduce((first, second) -> second).orElse(null);
			OffsetDateTime logoutTime = logoutEvent != null ? logoutEvent.getEventTime() : null;

			UserActivity lastEvent = sessionEvents.getLast();
			OffsetDateTime lastActiveTime = lastEvent.getEventTime();

			long durationMinutes = Duration.between(loginTime, logoutTime != null ? logoutTime : lastActiveTime).toMinutes();

			UserActivity latestPageView = sessionEvents.stream()
					.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
					.reduce((first, second) -> second).orElse(null);
			String currentScreen = latestPageView != null ? latestPageView.getScreenName() : "Dashboard";
			String currentModule = latestPageView != null ? latestPageView.getModuleName() : "Portal";
			if (currentModule == null || currentModule.isBlank() || "Unknown".equalsIgnoreCase(currentModule)) {
				currentModule = "Portal";
			}

			int pagesVisited = (int) sessionEvents.stream()
					.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
					.map(UserActivity::getScreenName)
					.filter(s -> s != null)
					.distinct()
					.count();

			String sessionStatus;
			if (logoutEvent != null) {
				sessionStatus = "Logged Out";
			} else {
				long minutesSinceLastEvent = Duration.between(lastActiveTime, now).toMinutes();
				if (minutesSinceLastEvent <= 5) {
					sessionStatus = "Active";
				} else if (minutesSinceLastEvent <= 30) {
					sessionStatus = "Inactive";
				} else {
					sessionStatus = "Logged Out";
				}
			}

			String ipAddress = sessionEvents.stream()
					.map(UserActivity::getIpAddress)
					.filter(ip -> ip != null && !ip.isBlank())
					.findFirst().orElse(null);
			String userAgent = sessionEvents.stream()
					.map(UserActivity::getUserAgent)
					.filter(ua -> ua != null && !ua.isBlank())
					.findFirst().orElse(null);

			String[] parsed = parseUserAgent(userAgent);

			List<UserActivitySummaryDto.ScreenDetailDto> screenDetails = buildScreenDetails(sessionEvents);

			summaries.add(UserActivitySummaryDto.builder()
					.userId(sessionEvents.getFirst().getUserId())
					.userName(sessionEvents.getFirst().getUserName())
					.sessionId(sessionId)
					.loginTime(loginTime.format(ISO_FMT))
					.logoutTime(logoutTime != null ? logoutTime.format(ISO_FMT) : null)
					.durationMinutes(durationMinutes)
					.currentScreen(currentScreen)
					.currentModule(currentModule)
					.pagesVisited(pagesVisited)
					.lastActiveTime(lastActiveTime.format(ISO_FMT))
					.status(sessionStatus)
					.ipAddress(ipAddress)
					.userAgent(userAgent)
					.browserName(parsed[0])
					.deviceType(parsed[1])
					.screenDetails(screenDetails)
					.build());
		}

		return summaries;
	}

	/**
	 * Build screen detail list with time-spent computation.
	 */
	private List<UserActivitySummaryDto.ScreenDetailDto> buildScreenDetails(List<UserActivity> events) {
		List<UserActivity> pageViews = events.stream()
				.filter(e -> "PAGE_VIEW".equals(e.getEventType()))
				.sorted(Comparator.comparing(UserActivity::getEventTime))
				.toList();

		List<UserActivitySummaryDto.ScreenDetailDto> details = new ArrayList<>();

		for (int i = 0; i < pageViews.size(); i++) {
			UserActivity current = pageViews.get(i);
			long timeSpentSeconds = 0;

			if (i < pageViews.size() - 1) {
				UserActivity next = pageViews.get(i + 1);
				timeSpentSeconds = Duration.between(current.getEventTime(), next.getEventTime()).getSeconds();
			}

			String modName = current.getModuleName();
			if (modName == null || modName.isBlank() || "Unknown".equalsIgnoreCase(modName)) {
				modName = "Portal";
			}

			details.add(UserActivitySummaryDto.ScreenDetailDto.builder()
					.screenName(current.getScreenName())
					.moduleName(modName)
					.timeSpentSeconds(timeSpentSeconds)
					.visitTime(current.getEventTime().format(ISO_FMT))
					.build());
		}

		return details;
	}

	/**
	 * Compute approximate total duration in minutes from page view events.
	 */
	private long computeTotalDuration(List<UserActivity> pageViewEvents) {
		if (pageViewEvents.size() < 2) return 0;

		// Group by session, then sum time between consecutive page views
		Map<String, List<UserActivity>> bySession = pageViewEvents.stream()
				.collect(Collectors.groupingBy(UserActivity::getSessionId));

		long totalSeconds = 0;
		for (var sessionEvents : bySession.values()) {
			List<UserActivity> sorted = sessionEvents.stream()
					.sorted(Comparator.comparing(UserActivity::getEventTime)).toList();
			for (int i = 0; i < sorted.size() - 1; i++) {
				long diff = Duration.between(sorted.get(i).getEventTime(), sorted.get(i + 1).getEventTime()).getSeconds();
				// Cap at 30 min to avoid counting idle time
				if (diff <= 1800) {
					totalSeconds += diff;
				}
			}
		}
		return totalSeconds / 60;
	}

	/**
	 * Build activity trend data grouped by granularity.
	 */
	private List<OverviewStatsDto.TrendPoint> buildActivityTrend(List<UserActivity> events,
			OffsetDateTime start, OffsetDateTime end, String granularity) {

		if (granularity == null) granularity = "hourly";

		DateTimeFormatter labelFmt;
		java.util.function.Function<OffsetDateTime, String> keyFn;

		switch (granularity.toLowerCase()) {
			case "daily":
				labelFmt = DateTimeFormatter.ofPattern("MMM dd");
				keyFn = t -> t.toLocalDate().toString();
				break;
			case "weekly":
				labelFmt = DateTimeFormatter.ofPattern("MMM dd");
				keyFn = t -> {
					LocalDate d = t.toLocalDate();
					return d.minus(d.getDayOfWeek().getValue() - 1, ChronoUnit.DAYS).toString();
				};
				break;
			default: // hourly
				labelFmt = DateTimeFormatter.ofPattern("HH:mm");
				keyFn = t -> t.truncatedTo(ChronoUnit.HOURS).format(DateTimeFormatter.ofPattern("HH:mm"));
				break;
		}

		// Group events by time bucket
		Map<String, List<UserActivity>> buckets = events.stream()
				.collect(Collectors.groupingBy(e -> keyFn.apply(e.getEventTime()),
						LinkedHashMap::new, Collectors.toList()));

		List<OverviewStatsDto.TrendPoint> trend = new ArrayList<>();
		for (var entry : buckets.entrySet()) {
			List<UserActivity> bucketEvents = entry.getValue();
			int users = (int) bucketEvents.stream().map(UserActivity::getUserId).distinct().count();
			int sessions = (int) bucketEvents.stream().map(UserActivity::getSessionId).distinct().count();
			int screenViews = (int) bucketEvents.stream()
					.filter(e -> "PAGE_VIEW".equals(e.getEventType())).count();

			trend.add(OverviewStatsDto.TrendPoint.builder()
					.label(entry.getKey())
					.users(users)
					.sessions(sessions)
					.screenViews(screenViews)
					.build());
		}

		return trend;
	}

	/**
	 * Convert DTO → Entity.
	 */
	private UserActivity toEntity(UserActivityDto dto) {
		UserActivity entity = new UserActivity();
		entity.setUserId(dto.getUserId());
		entity.setUserName(dto.getUserName());
		entity.setEventType(dto.getEventType());
		entity.setModuleName(dto.getModuleName());
		entity.setScreenName(dto.getScreenName());
		entity.setIpAddress(dto.getIpAddress());
		entity.setUserAgent(dto.getUserAgent());
		entity.setSessionId(dto.getSessionId());

		if (dto.getEventTime() != null && !dto.getEventTime().isBlank()) {
			try {
				entity.setEventTime(OffsetDateTime.parse(dto.getEventTime()));
			} catch (Exception e) {
				entity.setEventTime(OffsetDateTime.now(ZoneOffset.UTC));
			}
		} else {
			entity.setEventTime(OffsetDateTime.now(ZoneOffset.UTC));
		}

		return entity;
	}

	/**
	 * Parse User-Agent string into [browserName, deviceType].
	 */
	private String[] parseUserAgent(String userAgent) {
		String browserName = "Unknown";
		String deviceType = "Desktop";

		if (userAgent == null || userAgent.isBlank()) {
			return new String[] { browserName, deviceType };
		}

		String ua = userAgent.toLowerCase();

		if (ua.contains("edg/") || ua.contains("edge/")) {
			browserName = "Edge";
		} else if (ua.contains("opr/") || ua.contains("opera")) {
			browserName = "Opera";
		} else if (ua.contains("chrome/") && !ua.contains("chromium")) {
			browserName = "Chrome";
		} else if (ua.contains("firefox/")) {
			browserName = "Firefox";
		} else if (ua.contains("safari/") && !ua.contains("chrome")) {
			browserName = "Safari";
		} else if (ua.contains("msie") || ua.contains("trident/")) {
			browserName = "Internet Explorer";
		}

		if ((ua.contains("mobile") || ua.contains("android")) && !ua.contains("tablet")) {
			deviceType = "Mobile";
		} else if (ua.contains("tablet") || ua.contains("ipad")) {
			deviceType = "Tablet";
		}

		return new String[] { browserName, deviceType };
	}
}

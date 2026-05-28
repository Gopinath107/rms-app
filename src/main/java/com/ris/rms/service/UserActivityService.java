package com.ris.rms.service;

import java.time.LocalDate;
import java.util.List;

import com.ris.rms.dto.DashboardStatsDto;
import com.ris.rms.dto.ModuleUsageDto;
import com.ris.rms.dto.OverviewStatsDto;
import com.ris.rms.dto.RealtimeActivityDto;
import com.ris.rms.dto.ScreenUsageDto;
import com.ris.rms.dto.SessionTimelineDto;
import com.ris.rms.dto.UserActivityDto;
import com.ris.rms.dto.UserActivitySummaryDto;

public interface UserActivityService {

	void recordEvent(UserActivityDto dto);

	void recordBatchEvents(List<UserActivityDto> events);

	List<UserActivitySummaryDto> getActivitySummary(Long userId, LocalDate from, LocalDate to,
			String module, String status);

	DashboardStatsDto getDashboardStats(LocalDate date);

	List<UserActivitySummaryDto.ScreenDetailDto> getUserSessionDetail(Long userId, String sessionId);

	// ── Analytics APIs ──────────────────────────────────────────────────

	RealtimeActivityDto getRealtimeActivity();

	OverviewStatsDto getOverviewStats(LocalDate from, LocalDate to, String granularity);

	List<ModuleUsageDto> getModuleUsage(LocalDate from, LocalDate to);

	List<ScreenUsageDto> getScreenUsage(LocalDate from, LocalDate to, String module);

	List<UserActivitySummaryDto> getSessionsList(Long userId, LocalDate from, LocalDate to,
			String module, String status);

	SessionTimelineDto getSessionTimeline(String sessionId);

	List<UserActivitySummaryDto> exportSessions(Long userId, LocalDate from, LocalDate to,
			String module, String status);
}

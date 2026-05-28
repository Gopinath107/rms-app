package com.ris.rms.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverviewStatsDto {

	private Integer totalUsers;
	private Integer activeUsers;
	private Integer totalSessions;
	private Double averageSessionDuration;
	private Integer totalScreenViews;
	private String mostUsedModule;
	private List<TrendPoint> activityTrend;
	private List<ModuleStat> moduleBreakdown;
	private List<ScreenStat> topScreens;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class TrendPoint {
		private String label;
		private Integer users;
		private Integer sessions;
		private Integer screenViews;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ModuleStat {
		private String moduleName;
		private Integer views;
		private Integer users;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ScreenStat {
		private String screenName;
		private String moduleName;
		private Integer views;
		private Integer uniqueUsers;
	}
}

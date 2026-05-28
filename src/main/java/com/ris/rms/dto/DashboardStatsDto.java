package com.ris.rms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {

	private Integer totalActiveUsersToday;
	private Integer totalLoggedInUsers;
	private Double averageUsageMinutes;
	private String mostUsedModule;
	private Integer recentlyActiveUsers;
	private Integer mostUsedModuleCount;
}

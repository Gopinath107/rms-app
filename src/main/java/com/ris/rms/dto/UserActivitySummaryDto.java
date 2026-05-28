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
public class UserActivitySummaryDto {

	private Long userId;
	private String userName;
	private String sessionId;
	private String loginTime;
	private String logoutTime;
	private Long durationMinutes;
	private String currentScreen;
	private String currentModule;
	private Integer pagesVisited;
	private String lastActiveTime;
	private String status;
	private String ipAddress;
	private String userAgent;
	private String browserName;
	private String deviceType;
	private List<ScreenDetailDto> screenDetails;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ScreenDetailDto {
		private String screenName;
		private String moduleName;
		private Long timeSpentSeconds;
		private String visitTime;
	}
}

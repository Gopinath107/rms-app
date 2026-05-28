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
public class SessionTimelineDto {

	private String sessionId;
	private Long userId;
	private String userName;
	private String loginTime;
	private String logoutTime;
	private Long durationMinutes;
	private String status;
	private String ipAddress;
	private String browserName;
	private String deviceType;
	private Integer pagesVisited;

	private List<TimelineEvent> events;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class TimelineEvent {
		private String eventType;
		private String screenName;
		private String moduleName;
		private String eventTime;
		private Long timeSpentSeconds;
	}
}

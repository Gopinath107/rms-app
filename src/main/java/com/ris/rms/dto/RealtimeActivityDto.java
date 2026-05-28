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
public class RealtimeActivityDto {

	private Integer activeUsersCount;
	private List<ActiveSession> activeSessions;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class ActiveSession {
		private Long userId;
		private String userName;
		private String sessionId;
		private String currentScreen;
		private String currentModule;
		private String lastActiveTime;
		private Long sessionDurationMinutes;
		private String deviceType;
		private String browserName;
		private String ipAddress;
		private String status;
	}
}

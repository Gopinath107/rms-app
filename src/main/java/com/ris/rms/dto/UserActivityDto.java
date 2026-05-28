package com.ris.rms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityDto {

	private Long userId;
	private String userName;
	private String eventType;
	private String moduleName;
	private String screenName;
	private String sessionId;
	private String eventTime;
	private String ipAddress;
	private String userAgent;
}

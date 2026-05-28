package com.ris.rms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleUsageDto {

	private String moduleName;
	private Integer totalUsers;
	private Integer totalSessions;
	private Integer totalViews;
	private Long totalDurationMinutes;
	private Double averageDurationMinutes;
	private String lastUsed;
}

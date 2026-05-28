package com.ris.rms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenUsageDto {

	private String screenName;
	private String moduleName;
	private Integer viewCount;
	private Integer uniqueUsers;
	private Long totalTimeSpentMinutes;
	private Double averageTimeSpentMinutes;
	private String lastAccessed;
}

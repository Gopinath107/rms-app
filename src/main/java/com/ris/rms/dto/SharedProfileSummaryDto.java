package com.ris.rms.dto;

import java.util.List;
import lombok.Data;

@Data
public class SharedProfileSummaryDto {
	private long todayEvents;
	private long todayUnique;
	private long yesterdayEvents;
	private long yesterdayUnique;
	private long totalEvents;
	private long totalUnique;
	private List<SharedProfileItemDto> records;
}


package com.ris.rms.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandRequestSummaryDto {
	private Long requestId;
	private String stage;
	private String stageReason;
	private OffsetDateTime lastUpdatedAt;
	private long pendingDays;
}
package com.ris.rms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class SharedProfileItemDto {
	private Long requestId;
	private Long demandId;
	private String demandTitle;
	private Long accountId;
	private String clientName;
	private Long resourceId;
	private String resourceType; // "INTERNAL" or "EXTERNAL"
	private String resourceName;
	private String resourceEmail;
	private String designation;
	private String experienceYears;
	private Long submittedByUserId;
	private String submittedByName;
	private String submittedByEmail;
	private LocalDate submittedDate;
	private OffsetDateTime submittedAt;
	private String status;
	private String interviewStage;
	private OffsetDateTime lastStatusUpdate;
	private List<Map<String, Object>> interviewLevels;
	private Map<String, Object> allocation;
}


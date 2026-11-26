package com.ris.rms.dto;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class ProfileHistoryEventDto {
	private String eventType; // SUBMITTED, SHORTLISTED, INTERVIEW_SCHEDULED, INTERVIEW_COMPLETED, SELECTED, REJECTED, ALLOCATED
	private String previousStatus;
	private String newStatus;
	private String actionByName;
	private String actionByEmail;
	private OffsetDateTime timestamp;
	private String remarks;
	private Integer rating;
	private String roundLevel;
}


package com.ris.rms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class DemandResponseDto {
	private Long demandid;
	private String demandTitle;
	private String description;
	private LocalDate demandOpenDt;
	private LocalDate fulfilmentDt;
	private LocalDate actualFulfilmentDt;
	private Boolean fulfilledWithinTarget;
	private Long companyId;
	private String companyName;
	private Long accountId;
	private String accountName;
	private String accountEmail;
	private Long departmentId;
	private String departmentName;
	private String projectName;
	
	// Requester Details
	private Long requesterUserId;
	private String requesterName;
	private String requesterEmail;

	// Updated By Details
	private Long updatedById;
	private String updatedByName;
	private String updatedByEmail;

	private String yearsofexp;
	private List<Long> skillIds;
	private List<String> skillName;

	private String roleDuration;
	private String locationType;
	private String workLocPref;
	private String workMode;
	private String priority;
	private String overallStatus;
	private OffsetDateTime createddt;
	private OffsetDateTime updateddt;
	private Integer resourceRequestsCount;  // Required headcount target (e.g. 5)
	private long submittedProfilesCount;    // Actual resumes shared so far (e.g. 12)
	private long pendingDays;
	
	private List<DemandRequestSummaryDto> requestsSummary;
	private DemandStageCountsDto stageCounts;
	
	// Resume Share Details
	private List<ResumeShareInfo> sharedResumes;

	@Data
	public static class ResumeShareInfo {
		private Long resourceId;       // Employee ID or Candidate ID
		private String resourceName;
		private String resourceType;   // "EMPLOYEE" or "CANDIDATE"
		private String resourceEmail;
		private String sharedBy;       // Name of the person who shared
		private String sharedByEmail;
		private OffsetDateTime sharedAt;
	}
}
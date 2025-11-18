package com.ris.rms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class DemandResponseDto {
	private Long demandid;
	private String demandTitle;
	private LocalDate demandOpenDt;
	private Long companyId;
	private String companyName;
	private Long accountId;
	private String accountName;
	private String accountEmail;
	private Long departmentId;
	private String departmentName;
	private String projectName;
	private Long requesterUserId;
	private String requesterName;
	private String requesterEmail;
	private String yearsofexp;
	private List<Long> skillIds;
	private List<String> skillName;
	private LocalDate fulfilmentDt;
	private String roleDuration;
	private String locationType;
	private String workLocPref;
	private String workMode;
	private String priority;
	private String overallStatus;
	private OffsetDateTime createddt;
	private OffsetDateTime updateddt;
	private Integer resourceRequestsCount;
	private long pendingDays;
	private List<DemandRequestSummaryDto> requestsSummary;
	private DemandStageCountsDto stageCounts;
}
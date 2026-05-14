package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ResourceRequestDto {
	private Long requestId;

	private Long projectId;
	private String projectName;
	private Long companyId;
	private String companyName;
	private Long accountId;
	private String accountName;

	private Long requesterUserId;
	private String requesterName;
	private String requesterEmail;

	@NotNull(message = "numberOfResources is required")
	@Min(value = 1, message = "numberOfResources must be > 0")
	private Integer numberOfResources;

	private String experienceRange;
	private String locationType;
	private String workMode;
	private String location;
	private String priority;

	private String status;
	private LocalDate submittedDate;

	private BigDecimal estimatedCostTotal;
	private BigDecimal estimatedCostPerResourceMonth;

	private List<Long> primarySkillIds;
	private List<String> primarySkills;
	private List<Long> secondarySkillIds;
	private List<String> secondarySkills;

	private List<Long> skillIds;
	private List<String> skills;

	private Long groupId;
	private String groupTitle;
	private Long demandId;
	private String demandTitle;
	private String demandDescription;
	private Long daysPending;

	// Resource/Candidate linkage (populated when created via resume sharing)
	private Long employeeId;        // set for INTERNAL resource
	private Long candidateId;       // set for EXTERNAL candidate
	private String resourceType;    // "INTERNAL" or "EXTERNAL"
	private String candidateName;   // resolved name for display (employee or candidate full name)
}

package com.ris.rms.dto;

import java.math.BigDecimal;
import java.util.List;
import com.ris.rms.dto.ProjectDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResourceRequestGroupDto {
	private Long groupId;

	private Long companyId;
	private String companyName;
	private ProjectDto projectDetails;

	private Long createdBy;
	private String createdByName;
	private String createdByEmail;

	private String title;
	private Integer totalRequested;
	private String status;
	private String createdAt;
	private String experienceRange;
	private String locationType;
	private String workMode;
	private String location;
	private String priority;	
	private BigDecimal estimatedCostTotal; 
	private BigDecimal estimatedCostPerResourceMonth;
	private List<Long> primarySkillIds;
    private List<Long> secondarySkillIds;
    private List<Long> skillIds;
    private Long daysPending; 
    private Long daysToApprove;
}

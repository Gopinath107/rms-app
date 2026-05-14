package com.ris.rms.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class BulkCreateResReqDto {

	private Long projectId;
	private Long requesterUserId;
	private Integer count;
	private String experienceRange;
	private String locationType;
	private String workMode;
	private String location;
	private String priority;

	private BigDecimal estimatedCostTotal;
	private BigDecimal estimatedCostPerResourceMonth;

	private List<Long> skillIds;
	private List<Long> primarySkillIds;
	private List<Long> secondarySkillIds;
	private Boolean autoSubmit;
	private String groupTitle;
}

package com.ris.rms.dto;

import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DemandCreateDto {
	@NotNull
	private Long companyId;
	@NotNull
	private Long requesterUserId;
	@NotNull
	private Long accountId;
	@NotNull
	private Long departmentId;

	private String projectName;

	@NotBlank
	private String demandTitle;

	private String yearsofexp;
	private List<Long> skillIds;
	private String roleDuration;
	private String workLocPref;

	private String priority;
	private String locationType;
	private String workMode;

	@NotNull
	@Min(1)
	private Integer resourceRequests;
}
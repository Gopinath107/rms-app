package com.ris.rms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeShareDto {

	@NotNull(message = "employeeId is required")
	private Long employeeId;

	@NotBlank(message = "status is required")
	private String status;

	private List<Long> groupIds;
	private List<Long> demandIds;
	
	@NotNull(message = "actionByUserId is required")
	private Long actionByUserId;

	private String employeeName;
	private String statusSet;
	private String actionByUserName;
	private OffsetDateTime actionAt;

	private List<Map<String, Object>> sharedWith;
}
package com.ris.rms.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AllocationDto {

	private Long allocationId;

	private Long projectId;
	private String projectName;

	private Long accountId;
	private String accountName;

	private Long companyId;
	private String companyName;

	private Long employeeId;
	private String employeeName;

	private Long requestId;

	private String projectRole;
	private Boolean isBillable;
	private LocalDate startDate;
	private LocalDate endDate;
	private String status;
}

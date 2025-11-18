package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProjectDto {
	private Long projectId;

	@NotNull(message = "companyId is required")
	private Long companyId;
	private String companyName;

	private Long accountId;
	private String accountName;

	private Long managerUserId;
	private String managerName;

	@NotBlank(message = "projectName is required")
	@Size(max = 255)
	private String projectName;

	private String description;

	private LocalDate startDate;
	private LocalDate endDate;

	@DecimalMin(value = "0.0", inclusive = true, message = "budget must be >= 0")
	private BigDecimal budget;

	@DecimalMin(value = "0.0", inclusive = true, message = "revenueAmount must be >= 0")
	private BigDecimal revenueAmount;

	@Size(max = 50)
	private String priority;

	@Size(max = 50)
	private String status;

	private java.util.List<Long> skillIds;
	private java.util.List<String> skills;

	private Long managerEmployeeId;
	private String managerEmail;
}

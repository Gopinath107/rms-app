package com.ris.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartmentDto {
	private Long departmentId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	private Long parentDepartmentId;

	@NotBlank(message = "departmentName is required")
	@Size(max = 255, message = "departmentName must be <= 255 chars")
	private String departmentName;
}

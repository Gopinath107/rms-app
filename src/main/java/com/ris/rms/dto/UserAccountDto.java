package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class UserAccountDto {
	private Long userId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	@NotNull(message = "employeeId is required")
	private Long employeeId;

	private String employeeName;

	// Single-role fields — kept for backward compat in list/response serialization
	private Long roleId;
	private String roleName;

	// Multi-role field — used in create/update requests
	private List<Long> roleIds;

	@Email(message = "email must be valid")
	@NotBlank(message = "email is required")
	@Size(max = 255)
	private String email;

	@Size(max = 255)
	private String passwordHash;

	private Boolean isActive;
}

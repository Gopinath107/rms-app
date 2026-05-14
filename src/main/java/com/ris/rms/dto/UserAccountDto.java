package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserAccountDto {
	private Long userId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	@NotNull(message = "employeeId is required")
	private Long employeeId;

	private String employeeName;

	@NotNull(message = "roleId is required")
	private Long roleId;

	private String roleName;

	@Email(message = "email must be valid")
	@NotBlank(message = "email is required")
	@Size(max = 255)
	private String email;

	@Size(max = 255)
	private String passwordHash;

	private Boolean isActive;
}

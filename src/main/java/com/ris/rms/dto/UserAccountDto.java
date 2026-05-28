package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UserAccountDto {
	private Long userId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	// optional — a user can be created without an employee record
	private Long employeeId;

	private String employeeName;

	private String name;

	// ── Single-role convenience fields (backward compat) ──────────────────
	// roleId is NO LONGER required — populate from roleIds list
	private Long roleId;
	private String roleName;

	// ── Multi-role fields ─────────────────────────────────────────────────
	/** Full list of role IDs being assigned / returned */
	private List<Long> roleIds;

	/** Role names matching the roleIds list (same order) */
	private List<String> roleNames;

	/** Rich role objects: [{roleId, roleName}] — used by the UI to render badges */
	private List<Map<String, Object>> roles;

	@Email(message = "email must be valid")
	@NotBlank(message = "email is required")
	@Size(max = 255)
	private String email;

	@Size(max = 255)
	private String passwordHash;

	private Boolean isActive;
}

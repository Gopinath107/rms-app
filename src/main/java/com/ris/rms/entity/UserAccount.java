package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "user_account", schema = "rms")
public class UserAccount {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "employee_id", nullable = false)
	private Long employeeId;
	@Column(name = "company_id", nullable = false)
	private Long companyId;
	@Column(name = "role_id", nullable = false)
	private Long roleId;
	@Column(name = "email", nullable = false)
	private String email;
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;
	@Column(name = "is_active", nullable = false)
	private Boolean isActive;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private java.time.OffsetDateTime createdAt;
}

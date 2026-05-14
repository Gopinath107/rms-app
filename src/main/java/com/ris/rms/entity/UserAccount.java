package com.ris.rms.entity;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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
	
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "role_ids", columnDefinition = "bigint[]")
	private List<Long> roleIds = new ArrayList<>();
	
	@Column(name = "email", nullable = false)
	private String email;
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;
	@Column(name = "is_active", nullable = false)
	private Boolean isActive;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private java.time.OffsetDateTime createdAt;
	
	public Long getRoleId() {
		if (this.roleIds != null && !this.roleIds.isEmpty()) {
			return this.roleIds.get(0); 
		}
		return null;
	}

	public void setRoleId(Long roleId) {
		if (this.roleIds == null) {
			this.roleIds = new ArrayList<>();
		}
		if (this.roleIds.isEmpty()) {
			this.roleIds.add(roleId);
		} else {
			this.roleIds.set(0, roleId);
		}
	}
}

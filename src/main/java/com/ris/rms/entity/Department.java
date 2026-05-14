package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "department", schema = "rms", uniqueConstraints = @UniqueConstraint(name = "uk_department_company_name", columnNames = {
		"company_id", "department_name" }))
public class Department {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@Column(name = "parent_department_id")
	private Long parentDepartmentId;

	@Column(name = "department_name", nullable = false, length = 255)
	private String departmentName;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;
}

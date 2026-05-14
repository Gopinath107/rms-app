package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "role", schema = "rms", uniqueConstraints = @UniqueConstraint(name = "uk_role_company_name", columnNames = {
		"company_id", "role_name" }))
public class Role {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "role_id")
	private Long roleId;

	@Column(name = "company_id", nullable = false)
	private Long companyId; 

	@Column(name = "role_name", nullable = false, length = 100)
	private String roleName;
}

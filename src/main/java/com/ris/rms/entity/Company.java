package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "company", schema = "rms", uniqueConstraints = @UniqueConstraint(name = "uk_company_name", columnNames = "company_name"))
public class Company {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "company_id")
	private Long companyId;

	@Column(name = "company_name", nullable = false, length = 255)
	private String companyName;

	@Column(name = "company_email", length = 255)
	private String companyEmail;

	@Column(name = "address")
	private String address;

	@Column(name = "created_at", insertable = false, updatable = false)
	private OffsetDateTime createdAt;
}

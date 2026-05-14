package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "project", schema = "rms", uniqueConstraints = @UniqueConstraint(name = "uk_project_company_name", columnNames = {
		"company_id", "project_name" }))
public class Project {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "company_id", nullable = false)
	private Long companyId; 

	@Column(name = "account_id")
	private Long accountId; 

	@Column(name = "manager_id")
	private Long managerUserId; 

	@Column(name = "project_name", nullable = false, length = 255)
	private String projectName;

	@Column(name = "description")
	private String description;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Column(name = "budget", precision = 15, scale = 2)
	private BigDecimal budget;

	@Column(name = "revenue_amount", precision = 15, scale = 2)
	private BigDecimal revenueAmount;

	@Column(name = "priority", length = 50)
	private String priority; 

	@Column(name = "status", length = 50)
	private String status; 
}

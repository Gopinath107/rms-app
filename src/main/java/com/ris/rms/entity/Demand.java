package com.ris.rms.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
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
@Table(name = "demand", schema = "rms")
public class Demand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "demandid")
	private Long demandid;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@Column(name = "requester_user_id")
	private Long requesterUserId;

	@Column(name = "account_id")
	private Long accountId;

	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "demandtitle", nullable = false)
	private String demandtitle;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@CreationTimestamp
	@Column(name = "demandopendt", nullable = false, updatable = false)
	private LocalDate demandopendt;

	@Column(name = "actual_fulfilment_dt")
	private LocalDate actualFulfilmentDt;

	@Column(name = "project_name")
	private String projectName;

	@Column(name = "yearsofexp", length = 50)
	private String yearsofexp;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "skill_ids", columnDefinition = "bigint[]")
	private List<Long> skillIds;

	@Column(name = "roleduration", length = 100)
	private String roleduration;

	@Column(name = "worklocpref")
	private String worklocpref;

	@Column(name = "priority", length = 50)
	private String priority;

	@Column(name = "location_type", length = 50)
	private String locationType;

	@Column(name = "work_mode", length = 50)
	private String workMode;

	@Column(name = "overall_status", length = 50)
	private String overallStatus;

	@Column(name = "budget")
	private Double budget;

	@Column(name = "fulfilmentdt")
	private LocalDate fulfilmentdt;

	@Column(name = "resource_requests_count", nullable = false)
	private Integer resourceRequestsCount;

	@CreationTimestamp
	@Column(name = "createddt", nullable = false, updatable = false)
	private OffsetDateTime createddt;

	@Column(name = "updateddt")
	private OffsetDateTime updateddt;

	@Column(name = "updatedby")
	private Long updatedby;
}
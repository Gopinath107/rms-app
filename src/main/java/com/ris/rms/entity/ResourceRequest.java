package com.ris.rms.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

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
@Table(name = "resource_request", schema = "rms")
public class ResourceRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "project_id", nullable = true)
	private Long projectId;

	@Column(name = "requester_id")
	private Long requesterUserId;  

	@Column(name = "number_of_resources", nullable = false)
	private Integer numberOfResources;

	@Column(name = "experience_range", length = 50)
	private String experienceRange;

	@Column(name = "location_type", length = 50)
	private String locationType;

	@Column(name = "work_mode", length = 50)
	private String workMode;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "priority", length = 50)
	private String priority;

	@Column(name = "status", nullable = false, length = 50)
	private String status = "Draft";

	@Column(name = "submitted_date", nullable = false, insertable = false, updatable = false)
	@Generated(GenerationTime.INSERT)
	private LocalDate submittedDate;

	@Column(name = "estimated_cost_total", precision = 15, scale = 2)
	private BigDecimal estimatedCostTotal;

	@Column(name = "estimated_cost_per_resource_month", precision = 12, scale = 2)
	private BigDecimal estimatedCostPerResourceMonth;

	@Column(name = "group_id")
	private Long groupId;
	
	@Column(name = "demand_id")
	private Long demandId;

	// Populated when this request is created via resume sharing
	@Column(name = "employee_id")
	private Long employeeId; // INTERNAL resource

	@Column(name = "candidate_id")
	private Long candidateId; // EXTERNAL candidate

	@Column(name = "resource_type", length = 20)
	private String resourceType; // "INTERNAL" or "EXTERNAL"

}

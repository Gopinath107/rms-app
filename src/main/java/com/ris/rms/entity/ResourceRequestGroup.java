package com.ris.rms.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
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
@Table(name = "resource_request_group", schema = "rms")
public class ResourceRequestGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_id")
	private Long groupId;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "title")
	private String title;

	@Column(name = "total_requested", nullable = false)
	private Integer totalRequested;

	@Column(name = "status")
	private String status;

	@CreationTimestamp 
	@Column(name = "created_at", nullable = false, updatable = false) 
	private OffsetDateTime createdAt;

	
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "request_template", columnDefinition = "jsonb")
	private Map<String, Object> requestTemplate;

	@Column(name = "hr_approved_at") 
	private OffsetDateTime hrApprovedAt;

}

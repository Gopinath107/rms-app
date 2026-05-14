package com.ris.rms.entity;

import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "interview", schema = "rms")
public class Interview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "interview_id")
	private Long interviewId;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(name = "employee_id")
	private Long employeeId;

	@Column(name = "candidate_id")
	private Long candidateId;

	@Column(name = "interviewer_id")
	private Long interviewerId;

	@Column(name = "interview_type", length = 100)
	private String interviewType;

	@Column(name = "scheduled_at")
	private OffsetDateTime scheduledAt;

	@Column(name = "notes")
	private String notes;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "planned_levels")
	private List<String> plannedLevels;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "level_progress", columnDefinition = "jsonb")
	private String levelProgress;

	@Column(name = "status", length = 50)	
	private String status;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "candidate_id", insertable = false, updatable = false)
	private Candidate candidate;
}

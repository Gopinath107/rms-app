package com.ris.rms.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewDto {

	private Long interviewId;

	private Long requestId;

	private Long employeeId;
	private Long candidateId;
	
	private String status;

	@JsonProperty("currentStatus")
	private String currentStatus;

	private String overallStatus;

	private String onboardingStatus;

	@JsonAlias({ "notes" })
	private String feedback;

	@JsonProperty("overallNotes")
	private String overallNotes;

	private List<String> interviewLevels;

	private Long projectId;

	private String projectName;

	private Long accountId;

	private String accountName;

	private Long companyId;

	private String companyName;

	private List<String> rescheduleLevels;

	private String employeeName;

	private String employeeEmail;

	private String candidateName;
	private String candidateEmail;
	
    private Long groupId;
    private String groupTitle;
    private Long demandId;
    private String demandTitle;
    
	@JsonAlias({ "levels" })
	private List<LevelProgressDto> levelProgress;

	private Long createdByUserId;

	private String createdByUserName;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long interviewerUserId;

	@JsonIgnore
	private String interviewType;

	@JsonIgnore
	private String interviewerName;

	@JsonIgnore
	private String interviewerEmail;

	@JsonIgnore
	private Long feedbackId;

	@JsonIgnore
	private Integer rating;

	@JsonIgnore
	private String recommendation;

	@JsonIgnore
	private String feedbackComments;

	@JsonIgnore
	private OffsetDateTime scheduledAt;

	@JsonProperty("scheduledAt")
	@JsonIgnore
	private String scheduledAtText;

	private Long daysPending;

	private Long daysToComplete;
}

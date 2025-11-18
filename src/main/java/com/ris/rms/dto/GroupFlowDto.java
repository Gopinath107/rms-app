package com.ris.rms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Data;


@Data
public class GroupFlowDto {
    private Long groupId;
    private String groupTitle;
    private OffsetDateTime groupCreatedAt;
    private Integer groupTotalRequested;
    private String groupStatus;
    private Long groupCreatorUserId;
    private String groupCreatorName;
    private String groupCreatorEmail;

    private Long companyId;
    private String companyName;
    private Long projectId;
    private String projectName;
    private Long accountId;
    private String accountName;

    private Integer summaryTotalRequests;
    private Integer summaryOpen;
    private Integer summaryInterviewing;
    private Integer summarySelected;
    private Integer summaryAllocated;
    private Integer summaryOnboarded;
    private Integer summaryRejected;
    private Integer summaryTotalInterviews;
    private Long summaryPendingDays;

    private Long requestId;
    private String requestStatus;

    private Long interviewId;
    private String interviewOverallStatus;

    private Long candidateEmployeeId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhoneNumber;
    private String candidateResumeStatus;

    private Long allocationId;
    private String allocationEmployeeName;
    private LocalDate allocationStartDate;
    private LocalDate allocationEndDate;
    private String allocationProjectRole;
    private Boolean allocationIsBillable;

    private String interviewLevel;
    private String interviewLevelStatus;
    private Long interviewerUserId;
    private String interviewerName;
    private String interviewerEmail;
    private String interviewNotes;
    private OffsetDateTime interviewCompletedAt;
    private LocalDate demandOpenDt;
	private LocalDate fulfilmentDt;
    private String priority;
    private String roleDuration;
}

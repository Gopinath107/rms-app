package com.ris.rms.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

	enum MailAction {
		CREATED, UPDATED, CANCELLED
	}

	boolean sendEmployeeInterviewMail(String to, String projectName, String accountName, String employeeName,
			String interviewType, String interviewDateRaw, MailAction action);

	boolean sendInterviewerNotificationMail(String to, String projectName, String accountName, String interviewerName,
			String candidateOrEmployeeName, String interviewType, String interviewDateRaw, MailAction action);

	CompletableFuture<Boolean> sendEmployeeInterviewMailAsync(String to, String projectName, String accountName,
			String employeeName, String interviewType, String interviewDateRaw, MailAction action);

	CompletableFuture<Boolean> sendInterviewerNotificationMailAsync(String to, String projectName, String accountName,
			String interviewerName, String candidateOrEmployeeName, String interviewType, String interviewDateRaw,
			MailAction action);

	CompletableFuture<Boolean> sendHrResReqCreatedAsync(String to, String hrName, String projectName,
			String accountName, Long requestId, String submittedDate, String priority, Integer numberOfResources,
			String experienceRange, String location, String workMode, String locationType, String primarySkillsCsv,
			String secondarySkillsCsv);

	CompletableFuture<Boolean> sendHrOpportunityCreatedAsync(
			String to,
			String hrName,
			String projectName,
			String accountName,
			String opportunityTitle,
			Integer totalRequested,
			String submittedDate,
			String priority,
			String experienceRange,
			String location,
			String workMode,
			String locationType,
			String primarySkillsCsv,
			String secondarySkillsCsv);

	CompletableFuture<Boolean> sendResumeShareEmailAsync(
			String to,
			String clientName,
			String employeeName,
			String projectName,
			String companyName,
			ResumeStorageService.ResumeResource resumeResource);

	CompletableFuture<Boolean> sendDemandReportEmailAsync(
			List<String> toEmails,
			List<String> ccEmails,
			String subject,
			String userName,
			List<Map<String, Object>> reportData,
			String dateRangeText);

	CompletableFuture<Boolean> sendPasswordResetOtpAsync(String to, String otp);
}

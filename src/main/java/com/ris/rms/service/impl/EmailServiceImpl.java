package com.ris.rms.service.impl;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ris.rms.service.EmailService;
import com.ris.rms.service.ResumeStorageService;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String senderEmail;

	private final Map<String, String> templateCache = new ConcurrentHashMap<>();

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter IN_FMT_DASH = DateTimeFormatter.ofPattern("dd-MM-uuuu HH-mm");
	private static final DateTimeFormatter IN_FMT_COLON = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
	private static final DateTimeFormatter OUT_FULL = DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a");
	private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
	private static final DateTimeFormatter OUT_TIME = DateTimeFormatter.ofPattern("hh:mm a");

	private static final String T_EMP = "templates/interview-employee.html";
	private static final String T_INT = "templates/interview-interviewer.html";
	private static final String T_RES_REQ = "templates/resreq-hr-created.html";
	private static final String T_OPP = "templates/opportunity-hr-created.html";
	private static final String T_RESUME = "templates/client-share-resume.html";
	private static final String T_DEMAND_REPORT = "templates/demand-report.html";
	private static final String T_PASSWORD_RESET = "templates/password-reset.html";

	@PostConstruct
	public void initTemplates() {
		loadToCache(T_EMP);
		loadToCache(T_INT);
		loadToCache(T_RES_REQ);
		loadToCache(T_OPP);
		loadToCache(T_RESUME);
		loadToCache(T_DEMAND_REPORT);
		loadToCache(T_PASSWORD_RESET);
		log.info("All email templates pre-loaded into memory successfully.");
	}

	private void loadToCache(String path) {
		try (InputStream is = new ClassPathResource(path).getInputStream()) {
			templateCache.put(path, new String(is.readAllBytes(), StandardCharsets.UTF_8));
		} catch (Exception e) {
			log.error("Failed to load email template: {}", path, e);
		}
	}

	private String getTemplate(String path) {
		return templateCache.getOrDefault(path, "");
	}

	@Override
	public boolean sendEmployeeInterviewMail(String to, String projectName, String accountName, String employeeName,
			String interviewType, String interviewDateRaw, MailAction action) {
		try {
			Pretty when = normalizeDate(interviewDateRaw);
			String headline, intro, note;

			switch (action) {
				case CREATED -> {
					headline = "Interview Scheduled";
					intro = "Your interview for the project <strong>{{projectName}}</strong> has been scheduled.";
					note = "Please be available at least 10 minutes before the scheduled time.";
				}
				case UPDATED -> {
					headline = "Interview Updated";
					intro = "Your interview for the project <strong>{{projectName}}</strong> has been updated.";
					note = "Please review the new schedule and be available at the updated time.";
				}
				case CANCELLED -> {
					headline = "Interview Cancelled";
					intro = "Your interview for the project <strong>{{projectName}}</strong> has been cancelled.";
					note = "We will reach out if it is rescheduled.";
				}
				default -> {
					headline = "Interview Notification";
					intro = "Interview details are provided below.";
					note = "";
				}
			}

			String html = getTemplate(T_EMP).replace("{{headline}}", headline).replace("{{intro}}", intro)
					.replace("{{note}}", note).replace("{{employeeName}}", safe(employeeName))
					.replace("{{projectName}}", safe(projectName)).replace("{{accountName}}", safe(accountName))
					.replace("{{interviewType}}", safe(interviewType)).replace("{{interviewDate}}", safe(when.full))
					.replace("{{interviewDateOnly}}", safe(when.dateOnly))
					.replace("{{interviewTimeOnly}}", safe(when.timeOnly))
					.replace("{{companyName}}", "Rudhra Info Solutions");

			return sendHtmlEmail(to, headline + " - " + projectName, html);
		} catch (Exception e) {
			log.error("Error sending employee interview email to {}", to, e);
			return false;
		}
	}

	@Override
	public boolean sendInterviewerNotificationMail(String to, String projectName, String accountName,
			String interviewerName, String candidateOrEmployeeName, String interviewType, String interviewDateRaw,
			MailAction action) {
		try {
			Pretty when = normalizeDate(interviewDateRaw);
			String kicker, headline, intro, note;

			switch (action) {
				case CREATED -> {
					kicker = "Assignment • Action Required";
					headline = "New Interview Assigned";
					intro = "You are assigned to interview <strong>{{candidateName}}</strong>.";
					note = "Please conduct the session as scheduled and submit your feedback promptly.";
				}
				case UPDATED -> {
					kicker = "Assignment • Updated";
					headline = "Interview Updated";
					intro = "Your interview with <strong>{{candidateName}}</strong> has been updated.";
					note = "Please review the updated schedule and proceed accordingly.";
				}
				case CANCELLED -> {
					kicker = "Assignment • Cancelled";
					headline = "Interview Cancelled";
					intro = "Your interview with <strong>{{candidateName}}</strong> has been cancelled.";
					note = "No further action is required.";
				}
				default -> {
					kicker = "Interview";
					headline = "Interview Notification";
					intro = "Details are below.";
					note = "";
				}
			}

			String html = getTemplate(T_INT).replace("{{kicker}}", kicker).replace("{{headline}}", headline)
					.replace("{{intro}}", intro).replace("{{note}}", note)
					.replace("{{interviewerName}}", safe(interviewerName))
					.replace("{{candidateName}}", safe(candidateOrEmployeeName))
					.replace("{{projectName}}", safe(projectName)).replace("{{accountName}}", safe(accountName))
					.replace("{{interviewType}}", safe(interviewType)).replace("{{interviewDate}}", safe(when.full))
					.replace("{{interviewDateOnly}}", safe(when.dateOnly))
					.replace("{{interviewTimeOnly}}", safe(when.timeOnly))
					.replace("{{companyName}}", "Rudhra Info Solutions");

			return sendHtmlEmail(to, headline + " - " + projectName, html);
		} catch (Exception e) {
			log.error("Error sending interviewer notification email to {}", to, e);
			return false;
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendEmployeeInterviewMailAsync(String to, String projectName, String accountName,
			String employeeName, String interviewType, String interviewDateRaw, MailAction action) {
		return CompletableFuture.completedFuture(sendEmployeeInterviewMail(to, projectName, accountName, employeeName,
				interviewType, interviewDateRaw, action));
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendInterviewerNotificationMailAsync(String to, String projectName,
			String accountName, String interviewerName, String candidateOrEmployeeName, String interviewType,
			String interviewDateRaw, MailAction action) {
		return CompletableFuture.completedFuture(sendInterviewerNotificationMail(to, projectName, accountName,
				interviewerName, candidateOrEmployeeName, interviewType, interviewDateRaw, action));
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendHrResReqCreatedAsync(String to, String hrName, String projectName,
			String accountName, Long requestId, String submittedDate, String priority, Integer numberOfResources,
			String experienceRange, String location, String workMode, String locationType, String primarySkillsCsv,
			String secondarySkillsCsv) {

		try {
			String html = getTemplate(T_RES_REQ).replace("{{hrName}}", safe(hrName))
					.replace("{{projectName}}", safe(projectName)).replace("{{accountName}}", safe(accountName))
					.replace("{{requestId}}", requestId == null ? "-" : "#" + requestId)
					.replace("{{submittedDate}}", safe(submittedDate)).replace("{{priority}}", safe(priority))
					.replace("{{numberOfResources}}",
							numberOfResources == null ? "-" : String.valueOf(numberOfResources))
					.replace("{{experienceRange}}", safe(experienceRange)).replace("{{location}}", safe(location))
					.replace("{{workMode}}", safe(workMode)).replace("{{locationType}}", safe(locationType))
					.replace("{{primarySkills}}", blankDash(primarySkillsCsv))
					.replace("{{secondarySkills}}", blankDash(secondarySkillsCsv))
					.replace("{{year}}", String.valueOf(LocalDate.now(IST).getYear()));

			boolean ok = sendHtmlEmail(to, "[RMS] New Resource Request • " + projectName, html);
			return CompletableFuture.completedFuture(ok);
		} catch (Exception e) {
			log.error("Error sending HR ResReq email to {}", to, e);
			return CompletableFuture.completedFuture(false);
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendHrOpportunityCreatedAsync(String to, String hrName, String projectName,
			String accountName, String opportunityTitle, Integer totalRequested, String submittedDate, String priority,
			String experienceRange, String location, String workMode, String locationType, String primarySkillsCsv,
			String secondarySkillsCsv) {

		try {
			String html = getTemplate(T_OPP).replace("{{hrName}}", safe(hrName))
					.replace("{{projectName}}", safe(projectName)).replace("{{accountName}}", safe(accountName))
					.replace("{{opportunityTitle}}", safe(opportunityTitle))
					.replace("{{totalRequested}}", totalRequested == null ? "-" : String.valueOf(totalRequested))
					.replace("{{submittedDate}}", safe(submittedDate)).replace("{{priority}}", blankDash(priority))
					.replace("{{experienceRange}}", safe(experienceRange)).replace("{{location}}", safe(location))
					.replace("{{workMode}}", safe(workMode)).replace("{{locationType}}", safe(locationType))
					.replace("{{primarySkills}}", blankDash(primarySkillsCsv))
					.replace("{{secondarySkills}}", blankDash(secondarySkillsCsv))
					.replace("{{year}}", String.valueOf(LocalDate.now(IST).getYear()));

			boolean ok = sendHtmlEmail(to, "[RMS] New Opportunity Resource Request • " + projectName, html);
			return CompletableFuture.completedFuture(ok);
		} catch (Exception e) {
			log.error("Error sending HR Opportunity email to {}", to, e);
			return CompletableFuture.completedFuture(false);
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendResumeShareEmailAsync(String to, String clientName, String employeeName,
			String projectName, String companyName, ResumeStorageService.ResumeResource resumeResource) {
		try {
			String html = getTemplate(T_RESUME).replace("{{clientName}}", safe(clientName))
					.replace("{{projectName}}", safe(projectName)).replace("{{employeeName}}", safe(employeeName))
					.replace("{{companyName}}", safe(companyName));

			boolean ok = sendEmailWithAttachment(to, "New Resume for " + projectName + ": " + employeeName, html,
					resumeResource.fileName(), resumeResource.resource());

			return CompletableFuture.completedFuture(ok);
		} catch (Exception e) {
			log.error("Error sending Resume Share email to {}", to, e);
			return CompletableFuture.completedFuture(false);
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendPasswordResetOtpAsync(String to, String otp) {
		try {
			String html = getTemplate(T_PASSWORD_RESET).replace("{{otp}}", safe(otp))
					.replace("{{year}}", String.valueOf(LocalDate.now(IST).getYear()));

			boolean ok = sendHtmlEmail(to, "[RMS] Password Reset Verification Code", html);
			return CompletableFuture.completedFuture(ok);
		} catch (Exception e) {
			log.error("Error sending Password Reset OTP email to {}", to, e);
			return CompletableFuture.completedFuture(false);
		}
	}

	private boolean sendHtmlEmail(String to, String subject, String htmlContent) {
		return sendHtmlEmail(List.of(to), null, subject, htmlContent);
	}

	private boolean sendHtmlEmail(List<String> toList, List<String> ccList, String subject, String htmlContent) {
		try {
			if (toList == null || toList.isEmpty()) {
				throw new IllegalArgumentException("To recipient list must not be empty");
			}

			List<String> cleanedTo = toList.stream().filter(Objects::nonNull).map(String::trim)
					.filter(s -> !s.isEmpty()).toList();

			if (cleanedTo.isEmpty()) {
				throw new IllegalArgumentException("To recipient list must contain at least one valid email address");
			}

			List<String> cleanedCc = Collections.emptyList();
			if (ccList != null && !ccList.isEmpty()) {
				cleanedCc = ccList.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
						.toList();
			}

			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(senderEmail, "Rudhra Info Solutions");
			helper.setTo(cleanedTo.toArray(new String[0]));
			if (!cleanedCc.isEmpty()) {
				helper.setCc(cleanedCc.toArray(new String[0]));
			}
			helper.setSubject(subject);
			helper.setText(htmlContent, true);

			ClassPathResource logo = new ClassPathResource("static/brand/ris-logo.png");
			if (logo.exists()) {
				helper.addInline("companyLogo", logo);
			}

			mailSender.send(message);
			return true;
		} catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
			log.error("Failed to send HTML email to: " + toList, e);
			return false;
		}
	}

	private boolean sendEmailWithAttachment(String to, String subject, String htmlContent, String attachmentName,
			Resource attachment) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

			helper.setFrom(senderEmail, "Rudhra Info Solutions");
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlContent, true);

			ClassPathResource logo = new ClassPathResource("static/brand/ris-logo.png");
			if (logo.exists()) {
				helper.addInline("companyLogo", logo);
			}

			if (attachment != null) {
				helper.addAttachment(attachmentName, attachment);
			}

			mailSender.send(message);
			return true;
		} catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
			log.error("Failed to send Attachment email to: " + to, e);
			return false;
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendDemandReportEmailAsync(List<String> toEmails, List<String> ccEmails,
			String subject, String userName, List<Map<String, Object>> reportData, String dateRangeText) {

		try {
			String template = getTemplate(T_DEMAND_REPORT);
			if (template.isBlank()) {
				log.error("Demand report template not found!");
				return CompletableFuture.completedFuture(false);
			}

			StringBuilder rows1 = new StringBuilder();
			for (Map<String, Object> item : reportData) {
				Map<String, Object> d = (Map<String, Object>) item.get("demandInfo");
				Map<String, Object> c = (Map<String, Object>) item.get("contextInfo");
				Map<String, Object> s = (Map<String, Object>) item.get("statusSummary");

				String desc = d.get("description") != null ? (String) d.get("description") : "(no description)";
				List<String> rIds = (List<String>) s.get("requestIds");

				// Demand meta
				Object demandId = d.get("demandId");
				String demandTitle = safe((String) d.get("title"));

				String accountName = safe((String) c.get("accountName"));
				String projectName = safe((String) c.get("projectName"));

				String openDt = safe((String) d.get("demandOpenDt"));
				String fulfilmentDt = safe((String) d.get("fulfilmentDt"));
				String actualFulfilDt = safe((String) d.get("actualFulfilmentDt"));

				StringBuilder reqBadges = new StringBuilder();
				if (rIds != null && !rIds.isEmpty()) {
					for (String rid : rIds) {
						reqBadges.append(
								"<span style='display:inline-block;font-size:11px;color:#111827;background:#e5e7eb;"
										+ "border-radius:4px;padding:2px 6px;margin:2px;'>"
										+ rid + "</span>");
					}
				}

				// 🔹 START ROW
				rows1.append("<tr>");

				// 1) Demand column
				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>")
						.append("<div style='font-size:12px;color:#111827;font-weight:600;'>");
				if (demandId != null) {
					rows1.append("#").append(demandId).append(" - ");
				}
				rows1.append(demandTitle)
						.append("</div>")
						.append("<div style='font-size:11px;color:#6b7280;margin-top:2px;'>")
						.append(desc)
						.append("</div>")
						.append("</td>");

				// 2) Account column
				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>")
						.append("<div style='font-size:11px;color:#111827;'>")
						.append(accountName)
						.append("</div>")
						.append("<div style='font-size:11px;color:#6b7280;'>")
						.append(projectName)
						.append("</div>")
						.append("</td>");

				// 3) Dates column
				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>")
						.append("<div style='font-size:11px;color:#4b5563;'>Open: <strong>")
						.append(blankDash(openDt))
						.append("</strong></div>")
						.append("<div style='font-size:11px;color:#4b5563;'>Planned: <strong>")
						.append(blankDash(fulfilmentDt))
						.append("</strong></div>")
						.append("<div style='font-size:11px;color:#4b5563;'>Actual: <strong>")
						.append(blankDash(actualFulfilDt))
						.append("</strong></div>")
						.append("</td>");

				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>");
				if (rIds != null && !rIds.isEmpty()) {
					rows1.append(reqBadges);
				} else {
					rows1.append("<span style='font-size:11px;color:#9ca3af;'>No requests</span>");
				}
				rows1.append("</td>");

				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>")
						.append("<div style='font-size:11px;color:#4b5563;'>Selected: <strong>")
						.append(s.get("selected")).append("</strong></div>")
						.append("<div style='font-size:11px;color:#4b5563;'>Allocated: <strong>")
						.append(s.get("allocated")).append("</strong></div>")
						.append("<div style='font-size:11px;color:#4b5563;'>Onboarded: <strong>")
						.append(s.get("onboarded")).append("</strong></div>")
						.append("</td>");

				List<Map<String, Object>> resumeEmployees = (List<Map<String, Object>>) s.get("resumeEmployees");

				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>");

				if (resumeEmployees != null && !resumeEmployees.isEmpty()) {
					for (Map<String, Object> emp : resumeEmployees) {
						Object empId = emp.get("employeeId");
						String empName = safe((String) emp.get("employeeName"));
						String rStatus = safe((String) emp.get("resumeStatus"));

						rows1.append("<div style='font-size:11px;color:#111827;margin-bottom:2px;'>");
						if (empId != null) {
							rows1.append("#").append(empId).append(" - ");
						}
						rows1.append(empName == null ? "" : empName);

						if (!rStatus.isEmpty()) {
							rows1.append(" <span style='font-size:10px;color:#6b7280;'>(")
									.append(rStatus)
									.append(")</span>");
						}
						rows1.append("</div>");
					}
				} else {
					rows1.append("<span style='font-size:11px;color:#9ca3af;'>No resume actions</span>");
				}

				rows1.append("</td>");

				Object resumeStatus = s.get("resumeStatus");
				Object sharedCount = s.get("resumeSharedCount");
				Object rejectedCount = s.get("resumeRejectedCount");

				rows1.append("<td style='padding:10px;border-bottom:1px solid #e5e7eb;vertical-align:top;'>")
						.append("<div style='margin-bottom:4px;'>")
						.append("<span style='display:inline-block;font-size:11px;font-weight:600;"
								+ "padding:2px 10px;border-radius:999px;background:#dbeafe;color:#1d4ed8;'>")
						.append(resumeStatus != null ? resumeStatus : "No Resumes")
						.append("</span>")
						.append("</div>")
						.append("<div style='font-size:11px;color:#4b5563;'>Shared: <strong>")
						.append(sharedCount != null ? sharedCount : 0)
						.append("</strong> | Rejected: <strong>")
						.append(rejectedCount != null ? rejectedCount : 0)
						.append("</strong></div>")
						.append("</td>");

				rows1.append("</tr>");
			}

			StringBuilder rows2 = new StringBuilder();
			for (Map<String, Object> item : reportData) {
				List<Map<String, Object>> pipelineRows = (List<Map<String, Object>>) item.get("pipelineRows");
				if (pipelineRows == null || pipelineRows.isEmpty())
					continue;

				for (Map<String, Object> row : pipelineRows) {
					String status = (String) row.get("interviewStatus");
					String statusStyle = "background:#ecfdf3;color:#166534;";

					if (status != null) {
						String lower = status.toLowerCase();
						if (lower.contains("rejected") || lower.contains("cancelled") || lower.contains("dropped")) {
							statusStyle = "background:#fef2f2;color:#991b1b;";
						} else if (lower.contains("scheduled") || lower.contains("progress")
								|| lower.contains("hold")) {
							statusStyle = "background:#eff6ff;color:#1e40af;";
						}
					}

					Object demandId = row.get("demandId");
					String demandTitle = safe((String) row.getOrDefault("demandTitle", ""));

					Object employeeId = row.get("employeeId");
					String candidateName = safe((String) row.get("candidateName"));
					String candidateEmail = safe((String) row.get("candidateEmail"));

					rows2.append("<tr style='background-color:#f9fafb;'>")

							// Demand: ID + Name
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append(demandId != null ? ("#" + demandId) : "-")
							.append("<br/><span style='font-size:10px;color:#6b7280;'>").append(demandTitle)
							.append("</span></td>")

							// Request ID
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append(row.get("requestId")).append("</td>")

							// Candidate: employeeId + name + email
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>");
					if (employeeId != null) {
						rows2.append("#").append(employeeId).append(" - ");
					}
					rows2.append(candidateName).append("<br/><span style='font-size:10px;color:#6b7280;'>")
							.append(candidateEmail).append("</span></td>")

							// Interview status chip
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append("<span style='").append(statusStyle)
							.append("padding:2px 6px;border-radius:999px;font-size:10px;'>")
							.append(status == null ? "In Progress" : status).append("</span></td>")

							// Allocated
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append(row.get("allocated")).append("</td>")

							// Onboarded
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append(row.get("onboarded")).append("</td>")

							// Resume status (per candidate)
							.append("<td style='padding:8px 10px;border-bottom:1px solid #e5e7eb;'>")
							.append(row.get("resumeStatus")).append("</td>")

							.append("</tr>");
				}

			}

			if (rows2.length() == 0) {
				rows2.append("<tr><td colspan='7' style='padding:10px;text-align:center;color:#6b7280;font-size:12px;'>"
						+ "No active pipeline candidates found for this period." + "</td></tr>");
			}

			String finalHtml = template.replace("{{SECTION_1_ROWS}}", rows1.toString())
					.replace("{{SECTION_2_ROWS}}", rows2.toString()).replace("{{DATE_RANGE}}", dateRangeText);

			boolean ok = sendHtmlEmail(toEmails, ccEmails, subject, finalHtml);
			return CompletableFuture.completedFuture(ok);

		} catch (Exception e) {
			log.error("Error generating demand report email", e);
			return CompletableFuture.completedFuture(false);
		}
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private static String blankDash(String s) {
		return (s == null || s.isBlank()) ? "—" : s;
	}

	private Pretty normalizeDate(String raw) {
		if (raw == null || raw.isBlank())
			return new Pretty("", "", "");
		LocalDateTime ldt = tryParse(raw);
		if (ldt == null)
			return new Pretty(raw, "", "");
		var zdt = ldt.atZone(IST);
		return new Pretty(OUT_FULL.format(zdt) + " IST", OUT_DATE.format(zdt), OUT_TIME.format(zdt));
	}

	private LocalDateTime tryParse(String text) {
		try {
			return LocalDateTime.parse(text, IN_FMT_DASH);
		} catch (Exception ignore) {
		}
		try {
			return LocalDateTime.parse(text, IN_FMT_COLON);
		} catch (Exception ignore) {
		}
		try {
			return OffsetDateTime.parse(text).atZoneSameInstant(IST).toLocalDateTime();
		} catch (Exception ignore) {
		}
		return null;
	}

	private record Pretty(String full, String dateOnly, String timeOnly) {
	}
}
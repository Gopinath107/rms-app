package com.ris.rms.service.impl;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ris.rms.service.EmailService;
import com.ris.rms.service.ResumeStorageService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String senderEmail;

	private volatile String EMPLOYEE_TMPL;
	private volatile String INTERVIEWER_TMPL;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter IN_FMT_DASH = DateTimeFormatter.ofPattern("dd-MM-uuuu HH-mm");
	private static final DateTimeFormatter IN_FMT_COLON = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
	private static final DateTimeFormatter OUT_FULL = DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a");
	private static final DateTimeFormatter OUT_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
	private static final DateTimeFormatter OUT_TIME = DateTimeFormatter.ofPattern("hh:mm a");

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

			String html = employeeHtml().replace("{{headline}}", headline).replace("{{intro}}", intro)
					.replace("{{note}}", note).replace("{{employeeName}}", safe(employeeName))
					.replace("{{projectName}}", safe(projectName)).replace("{{accountName}}", safe(accountName))
					.replace("{{interviewType}}", safe(interviewType)).replace("{{interviewDate}}", safe(when.full))
					.replace("{{interviewDateOnly}}", safe(when.dateOnly))
					.replace("{{interviewTimeOnly}}", safe(when.timeOnly))
					.replace("{{companyName}}", "Rudhra Info Solutions");

			return sendHtmlEmail(to, headline + " - " + projectName, html);
		} catch (Exception e) {
			System.err.println("Email (employee) error: " + e.getMessage());
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

			String html = interviewerHtml().replace("{{kicker}}", kicker).replace("{{headline}}", headline)
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
			System.err.println("Email (interviewer) error: " + e.getMessage());
			return false;
		}
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendEmployeeInterviewMailAsync(String to, String projectName, String accountName,
			String employeeName, String interviewType, String interviewDateRaw, MailAction action) {
		boolean ok = sendEmployeeInterviewMail(to, projectName, accountName, employeeName, interviewType,
				interviewDateRaw, action);
		return CompletableFuture.completedFuture(ok);
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendInterviewerNotificationMailAsync(String to, String projectName,
			String accountName, String interviewerName, String candidateOrEmployeeName, String interviewType,
			String interviewDateRaw, MailAction action) {
		boolean ok = sendInterviewerNotificationMail(to, projectName, accountName, interviewerName,
				candidateOrEmployeeName, interviewType, interviewDateRaw, action);
		return CompletableFuture.completedFuture(ok);
	}

	@Override
	public CompletableFuture<Boolean> sendHrResReqCreatedAsync(String to, String hrName, String projectName,
			String accountName, Long requestId, String submittedDate, String priority, Integer numberOfResources,
			String experienceRange, String location, String workMode, String locationType, String primarySkillsCsv,
			String secondarySkillsCsv) {
		return CompletableFuture.supplyAsync(() -> {
			String html = loadTemplateOrThrow("templates/resreq-hr-created.html").replace("{{hrName}}", safe(hrName))
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
			String subject = "[RMS] New Resource Request • " + projectName;
			return sendHtmlEmail(to, subject, html);
		});
	}

	@Override
	@Async("mailExecutor")
	public CompletableFuture<Boolean> sendHrOpportunityCreatedAsync(
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
	    String secondarySkillsCsv
	) {
	    String html = loadTemplateOrThrow("templates/opportunity-hr-created.html")
	        .replace("{{hrName}}", safe(hrName))
	        .replace("{{projectName}}", safe(projectName))
	        .replace("{{accountName}}", safe(accountName))
	        .replace("{{opportunityTitle}}", safe(opportunityTitle))
	        .replace("{{totalRequested}}", totalRequested == null ? "-" : String.valueOf(totalRequested))
	        .replace("{{submittedDate}}", safe(submittedDate))
	        .replace("{{priority}}", blankDash(priority))               
	        .replace("{{experienceRange}}", safe(experienceRange))
	        .replace("{{location}}", safe(location))
	        .replace("{{workMode}}", safe(workMode))
	        .replace("{{locationType}}", safe(locationType))
	        .replace("{{primarySkills}}", blankDash(primarySkillsCsv))
	        .replace("{{secondarySkills}}", blankDash(secondarySkillsCsv))
	        .replace("{{year}}", String.valueOf(java.time.LocalDate.now(IST).getYear()));

	    String subject = "[RMS] New Opportunity Resource Request • " + projectName;
	    boolean ok = sendHtmlEmail(to, subject, html);
	    return CompletableFuture.completedFuture(ok);
	}

	
	@Override
    @Async("mailExecutor")
    public CompletableFuture<Boolean> sendResumeShareEmailAsync(
            String to,
            String clientName,
            String employeeName,
            String projectName,
            String companyName,
            ResumeStorageService.ResumeResource resumeResource) {
        
        try {
            String html = loadTemplateOrThrow("templates/client-share-resume.html")
                    .replace("{{clientName}}", safe(clientName))
                    .replace("{{projectName}}", safe(projectName))
                    .replace("{{employeeName}}", safe(employeeName))
                    .replace("{{companyName}}", safe(companyName));

            String subject = "New Resume for " + projectName + ": " + employeeName;
            
            // Use helper method that supports attachments
            boolean ok = sendEmailWithAttachment(
                to, 
                subject, 
                html, 
                resumeResource.fileName(), 
                resumeResource.resource()
            );
            return CompletableFuture.completedFuture(ok);

        } catch (Exception e) {
            System.err.println("Resume share email failed: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }


    // This method already exists
	private boolean sendHtmlEmail(String to, String subject, String htmlContent) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(senderEmail, "Rudhra Info Solutions");
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(htmlContent, true);

			ClassPathResource logo = new ClassPathResource("static/brand/ris-logo.png");
			helper.addInline("companyLogo", logo);

			mailSender.send(message);
			return true;
		} catch (MessagingException | UnsupportedEncodingException e) {
			System.err.println("Mail send failed: " + e.getMessage());
			return false;
		}
	}

	private boolean sendEmailWithAttachment(
	        String to,
	        String subject,
	        String htmlContent,
	        String attachmentName,
	        org.springframework.core.io.Resource attachment) {
	    try {
	        MimeMessage message = mailSender.createMimeMessage();
	        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

	        helper.setFrom(senderEmail, "Rudhra Info Solutions");
	        helper.setTo(to);
	        helper.setSubject(subject);
	        helper.setText(htmlContent, true);

	        ClassPathResource logo = new ClassPathResource("static/brand/ris-logo.png");
	        helper.addInline("companyLogo", logo);

	        helper.addAttachment(attachmentName, attachment);

	        mailSender.send(message);
	        return true;
	    } catch (MessagingException | UnsupportedEncodingException e) {
	        System.err.println("Mail send (with attachment) failed: " + e.getMessage());
	        return false;
	    }
	}

    

	private String loadTemplateOrThrow(String path) {
		try {
			ClassPathResource resource = new ClassPathResource(path);
			try (InputStream is = resource.getInputStream()) {
				return new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read template: " + path, e);
		}
	}

	private String fmtPrettyDateTime(String raw) {
		if (raw == null || raw.isBlank())
			return "";
		LocalDateTime ldt = tryParse(raw);
		if (ldt == null)
			return raw;
		return OUT_FULL.format(ldt.atZone(IST)) + " IST";
	}

	public static String fmtPrettyDate(LocalDate date) {
		if (date == null)
			return "";
		return OUT_DATE.format(date);
	}

	private String employeeHtml() throws Exception {
		if (EMPLOYEE_TMPL == null) {
			synchronized (this) {
				if (EMPLOYEE_TMPL == null) {
					try (InputStream is = new ClassPathResource("templates/interview-employee.html").getInputStream()) {
						EMPLOYEE_TMPL = new String(is.readAllBytes(), StandardCharsets.UTF_8);
					}
				}
			}
		}
		return EMPLOYEE_TMPL;
	}

	private String interviewerHtml() throws Exception {
		if (INTERVIEWER_TMPL == null) {
			synchronized (this) {
				if (INTERVIEWER_TMPL == null) {
					try (InputStream is = new ClassPathResource("templates/interview-interviewer.html")
							.getInputStream()) {
						INTERVIEWER_TMPL = new String(is.readAllBytes(), StandardCharsets.UTF_8);
					}
				}
			}
		}
		return INTERVIEWER_TMPL;
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
		String full = OUT_FULL.format(zdt) + " IST";
		String dateOnly = OUT_DATE.format(zdt);
		String timeOnly = OUT_TIME.format(zdt);
		return new Pretty(full, dateOnly, timeOnly);
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
			OffsetDateTime odt = OffsetDateTime.parse(text);
			return odt.atZoneSameInstant(IST).toLocalDateTime();
		} catch (Exception ignore) {
		}
		return null;
	}

	private static final class Pretty {
		final String full, dateOnly, timeOnly;

		Pretty(String f, String d, String t) {
			full = f;
			dateOnly = d;
			timeOnly = t;
		}
	}
}

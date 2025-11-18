package com.ris.rms.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmployeeDto {
	private Long employeeId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	@NotBlank(message = "firstName is required")
	@Size(max = 100)
	private String firstName;

	@NotBlank(message = "lastName is required")
	@Size(max = 100)
	private String lastName;

	@Email(message = "email must be valid")
	@Size(max = 255)
	private String email;

	@Size(max = 50)
	private String phoneNumber;

	private Long departmentId;
	private String departmentName;

	@Size(max = 255)
	private String jobTitle;

	@Min(value = 0, message = "experienceYears must be >= 0")
	private Integer experienceYears;

	@Size(max = 255)
	private String location;

	private LocalDate joiningDate;

	@Size(max = 50)
	private String employmentType;

	@Size(max = 50)
	private String status;
	private String currentProject;
	private String currentClient;
	private List<ProjectHistoryDto> projectHistory = new ArrayList<>();

	private java.util.List<Long> skillIds;
	private java.util.List<String> skills;

	private String resumeUrl;
	private String resumeFileName;
	private String resumeMimeType;
	private String resumeUploadedAt;
	private Long resumeDocumentId;
	private String storageType;

	private Long currentProjectId;
	private Long currentAccountId;

	private String resumeStatus;
	private Long resumeShareActionByUserId;
	private String resumeShareActionByUserName;
	private String resumeShareActionAt;
	private List<Map<String, Object>> resumeShareAudit;

	@Size(max = 20)
	private String gender;

	@Email(message = "personalEmailId must be valid")
	@Size(max = 255)
	private String personalEmailId;

	@Size(max = 255)
	private String degrees;

	@Size(max = 255)
	private String specialization;
	private Integer yearOfPassing;
	private String profileSummary;
	private String trainingSummary;
	private String certificationSummary;
	private OffsetDateTime createdDt;
	private OffsetDateTime updatedDt;
}

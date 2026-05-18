package com.ris.rms.dto;

import java.math.BigDecimal;
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

	@Size(max = 100)
	private String middleName;

	@NotBlank(message = "lastName is required")
	@Size(max = 100)
	private String lastName;

	@Email(message = "email must be valid")
	@Size(max = 255)
	private String email;

	@Size(max = 50)
	private String phoneNumber;

	// Extended contact
	@Size(max = 10)
	private String primaryCountryCode;
	@Size(max = 30)
	private String primaryContactNo;
	@Size(max = 10)
	private String secondaryCountryCode;
	@Size(max = 30)
	private String secondaryContactNo;

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
	private List<EmployeeDocumentDto> documents;

	@Size(max = 20)
	private String gender;

	@Email(message = "personalEmailId must be valid")
	@Size(max = 255)
	private String personalEmailId;

	// Identity & Citizenship
	private LocalDate dateOfBirth;
	@Size(max = 100)
	private String countryOfCitizenship;
	@Size(max = 100)
	private String documentType;
	@Size(max = 100)
	private String documentNumber;
	@Size(max = 100)
	private String securityClearance;
	@Size(max = 10)
	private String visa;
	@Size(max = 100)
	private String visaType;

	// Address
	@Size(max = 100)
	private String country;
	@Size(max = 100)
	private String state;
	@Size(max = 100)
	private String city;
	@Size(max = 20)
	private String zipCode;
	@Size(max = 500)
	private String street;

	// Availability
	@Size(max = 100)
	private String availabilityToJoin;
	@Size(max = 255)
	private String interviewAvailability;

	// Education
	@Size(max = 255)
	private String degrees;
	@Size(max = 255)
	private String specialization;
	private Integer yearOfPassing;

	@Size(max = 100)
	private String highestQualification;
	@Size(max = 255)
	private String universityName;
	private LocalDate dateOfQualification;
	@Size(max = 100)
	private String usaDegree;

	// Work details
	@Size(max = 255)
	private String currentJobTitle;
	@Size(max = 255)
	private String mostRecentEmployer;
	private Integer totalExperience;
	@Size(max = 10)
	private String relocate;

	// Compensation
	@Size(max = 20)
	private String currency;
	@Size(max = 30)
	private String frequency;
	private BigDecimal sourcingRate;

	// Summaries
	private String profileSummary;
	private String trainingSummary;
	private String certificationSummary;
	private String resumeSummary;

	// Skills (List<String> serialized to/from JSON)
	private List<String> primarySkills;
	private List<String> secondarySkills;
	private String suggestedKeywords;

	// Social links: [{linkType, link}]
	private List<Map<String, String>> socialLinks;

	private OffsetDateTime createdDt;
	private OffsetDateTime updatedDt;
}


package com.ris.rms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CandidateDto {

	private Long candidateId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	@NotBlank(message = "firstName is required")
	private String firstName;

	private String lastName;
	private String fullName;
	@NotBlank(message = "email is required")
	@Email(message = "Invalid email format")
	private String email;

	private String phoneNumber;
	private String location;
	private Integer experienceYears;
	private String status;
	private String gender;
	private String degrees;
	private String specialization;
	private Integer yearOfPassing;
	private String profileSummary;
	private String trainingSummary;
	private String certificationSummary;
	private String sourceType;
	private String sourceName;

	private List<Long> skillIds;
	private List<String> skillNames;
	private List<String> primarySkills;
	private List<String> secondarySkills;

	private Long resumeDocumentId;
	private String resumeUrl;
	private String resumeFileName;
	private String resumeMimeType;
	private String resumeUploadedAt;
	private String storageType;

	private String resumeStatus;
	private Long resumeShareActionByUserId;
	private String resumeShareActionByUserName;
	private String resumeShareActionAt;
	private List<Map<String, Object>> resumeShareAudit;
	private List<EmployeeDocumentDto> documents;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
	
	private String currentCompany;
    private Double currentCtc;
    private Double expectedCtc;
    private String noticePeriod;
    private String preferredLocation;
    private String personalEmailId;
    private String comments;

    private String middleName;
    private String dateOfBirth;
    private String primaryCountryCode;
    private String primaryContactNo;
    private String secondaryCountryCode;
    private String secondaryContactNo;
    private String countryOfCitizenship;
    private String documentType;
    private String documentNumber;
    private String securityClearance;
    private String visa;
    private String visaType;
    private String country;
    private String state;
    private String city;
    private String zipCode;
    private String street;
    private String availabilityToJoin;
    private String interviewAvailability;
    private String highestQualification;
    private String universityName;
    private String dateOfQualification;
    private String usaDegree;
    private String currentJobTitle;
    private String mostRecentEmployer;
    private Double totalExperience;
    private String relocate;
    private String currency;
    private String frequency;
    private Double sourcingRate;
    private String resumeSummary;
    private String suggestedKeywords;
    private String socialLinks;
    private Long currentAccountId;
}

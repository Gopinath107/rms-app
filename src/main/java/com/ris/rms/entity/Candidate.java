package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "candidate", schema = "rms")
public class Candidate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "candidate_id")
	private Long candidateId;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "phone_number", length = 13)
	private String phoneNumber;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "experience_years")
	private Integer experienceYears;

	@Column(name = "status", length = 50)
	private String status;

	@Column(name = "gender", length = 20)
	private String gender;

	@Column(name = "degrees", length = 255)
	private String degrees;

	@Column(name = "specialization", length = 255)
	private String specialization;

	@Column(name = "yearofpassing")
	private Integer yearOfPassing;

	@Column(name = "profilesummary")
	private String profileSummary;

	@Column(name = "trainingsummary")
	private String trainingSummary;

	@Column(name = "certificationsummary")
	private String certificationSummary;

	@Column(name = "source_type")
	private String sourceType; 

	@Column(name = "source_name")
	private String sourceName;
	
	@Column(name = "createddt", insertable = false, updatable = false)
	private OffsetDateTime createdDt;

	@Column(name = "updateddt")
	private OffsetDateTime updatedDt;

	@Column(name = "current_company")
    private String currentCompany;

    @Column(name = "current_ctc")
    private Double currentCtc;

    @Column(name = "expected_ctc")
    private Double expectedCtc;

    @Column(name = "notice_period")
    private String noticePeriod;

    @Column(name = "preferred_location")
    private String preferredLocation;

    @Column(name = "personal_email_id", length = 255)
    private String personalEmailId;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "primary_country_code", length = 10)
    private String primaryCountryCode;

    @Column(name = "primary_contact_no", length = 20)
    private String primaryContactNo;

    @Column(name = "secondary_country_code", length = 10)
    private String secondaryCountryCode;

    @Column(name = "secondary_contact_no", length = 20)
    private String secondaryContactNo;

    @Column(name = "country_of_citizenship", length = 100)
    private String countryOfCitizenship;

    @Column(name = "document_type", length = 50)
    private String documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "security_clearance", length = 100)
    private String securityClearance;

    @Column(name = "visa", length = 50)
    private String visa;

    @Column(name = "visa_type", length = 50)
    private String visaType;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "street", length = 255)
    private String street;

    @Column(name = "availability_to_join", length = 100)
    private String availabilityToJoin;

    @Column(name = "interview_availability", length = 100)
    private String interviewAvailability;

    @Column(name = "highest_qualification", length = 255)
    private String highestQualification;

    @Column(name = "university_name", length = 255)
    private String universityName;

    @Column(name = "date_of_qualification")
    private String dateOfQualification;

    @Column(name = "usa_degree", length = 50)
    private String usaDegree;

    @Column(name = "current_job_title", length = 255)
    private String currentJobTitle;

    @Column(name = "most_recent_employer", length = 255)
    private String mostRecentEmployer;

    @Column(name = "total_experience")
    private Double totalExperience;

    @Column(name = "relocate", length = 50)
    private String relocate;

    @Column(name = "currency", length = 20)
    private String currency;

    @Column(name = "frequency", length = 50)
    private String frequency;

    @Column(name = "sourcing_rate")
    private Double sourcingRate;

    @Column(name = "resume_summary", columnDefinition = "TEXT")
    private String resumeSummary;

    @Column(name = "suggested_keywords", columnDefinition = "TEXT")
    private String suggestedKeywords;

    @Column(name = "social_links_json", columnDefinition = "JSON")
    private String socialLinksJson;

    @Column(name = "current_account_id")
    private Long currentAccountId;
    
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdDt == null) {
            this.createdDt = now;
        }
        if (this.updatedDt == null) {
            this.updatedDt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedDt = OffsetDateTime.now();
    }
}
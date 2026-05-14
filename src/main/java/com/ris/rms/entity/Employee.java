package com.ris.rms.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "employee", schema = "rms", uniqueConstraints = {
		@UniqueConstraint(name = "uk_emp_company_email", columnNames = { "company_id", "email" }),
		@UniqueConstraint(name = "uk_emp_company_empid", columnNames = { "company_id", "employee_id" }),
		@UniqueConstraint(name = "uq_employee_personalemail", columnNames = { "company_id", "personalemailid" }) })
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "employee_id")
	private Long employeeId;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "middle_name", length = 100)
	private String middleName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "phone_number", length = 50)
	private String phoneNumber;

	// --- Extended contact ---
	@Column(name = "primary_country_code", length = 10)
	private String primaryCountryCode;

	@Column(name = "primary_contact_no", length = 30)
	private String primaryContactNo;

	@Column(name = "secondary_country_code", length = 10)
	private String secondaryCountryCode;

	@Column(name = "secondary_contact_no", length = 30)
	private String secondaryContactNo;

	@Column(name = "department_id")
	private Long departmentId;

	@Column(name = "job_title", length = 255)
	private String jobTitle;

	@Column(name = "experience_years")
	private Integer experienceYears;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "joining_date")
	private LocalDate joiningDate;

	@Column(name = "employment_type", length = 50)
	private String employmentType;

	@Column(name = "status", length = 50)
	private String status;

	@Column(name = "gender", length = 20)
	private String gender;

	@Column(name = "personalemailid", length = 255)
	private String personalemailid;

	// --- Identity & Citizenship ---
	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "country_of_citizenship", length = 100)
	private String countryOfCitizenship;

	@Column(name = "document_type", length = 100)
	private String documentType;

	@Column(name = "document_number", length = 100)
	private String documentNumber;

	@Column(name = "security_clearance", length = 100)
	private String securityClearance;

	@Column(name = "visa", length = 10)
	private String visa;

	@Column(name = "visa_type", length = 100)
	private String visaType;

	// --- Address ---
	@Column(name = "country", length = 100)
	private String country;

	@Column(name = "state", length = 100)
	private String state;

	@Column(name = "city", length = 100)
	private String city;

	@Column(name = "zip_code", length = 20)
	private String zipCode;

	@Column(name = "street", length = 500)
	private String street;

	// --- Availability ---
	@Column(name = "availability_to_join", length = 100)
	private String availabilityToJoin;

	@Column(name = "interview_availability", length = 255)
	private String interviewAvailability;

	// --- Education ---
	@Column(name = "degrees", length = 255)
	private String degrees;

	@Column(name = "specialization", length = 255)
	private String specialization;

	@Column(name = "yearofpassing")
	private Integer yearofpassing;

	@Column(name = "highest_qualification", length = 100)
	private String highestQualification;

	@Column(name = "university_name", length = 255)
	private String universityName;

	@Column(name = "date_of_qualification")
	private LocalDate dateOfQualification;

	@Column(name = "usa_degree", length = 100)
	private String usaDegree;

	// --- Work Details ---
	@Column(name = "current_job_title", length = 255)
	private String currentJobTitle;

	@Column(name = "most_recent_employer", length = 255)
	private String mostRecentEmployer;

	@Column(name = "total_experience")
	private Integer totalExperience;

	@Column(name = "relocate", length = 10)
	private String relocate;

	// --- Compensation ---
	@Column(name = "currency", length = 20)
	private String currency;

	@Column(name = "frequency", length = 30)
	private String frequency;

	@Column(name = "sourcing_rate")
	private java.math.BigDecimal sourcingRate;

	// --- Summaries ---
	@Column(name = "profilesummary", columnDefinition = "TEXT")
	private String profilesummary;

	@Column(name = "trainingsummary", columnDefinition = "TEXT")
	private String trainingsummary;

	@Column(name = "certificationsummary", columnDefinition = "TEXT")
	private String certificationsummary;

	@Column(name = "resume_summary", columnDefinition = "TEXT")
	private String resumeSummary;

	// --- Skills (JSON arrays stored as TEXT) ---
	@Column(name = "primary_skills", columnDefinition = "TEXT")
	private String primarySkillsJson;  // JSON: ["Java","React"]

	@Column(name = "secondary_skills", columnDefinition = "TEXT")
	private String secondarySkillsJson;

	@Column(name = "suggested_keywords", columnDefinition = "TEXT")
	private String suggestedKeywords;

	// --- Social Links (JSON array stored as TEXT) ---
	// Format: [{"linkType":"LinkedIn","link":"https://..."}]
	@Column(name = "social_links", columnDefinition = "TEXT")
	private String socialLinksJson;

	@CreationTimestamp
	@Column(name = "createddt", updatable = false)
	private OffsetDateTime createddt;

	@Column(name = "updateddt")
	private OffsetDateTime updateddt;
}

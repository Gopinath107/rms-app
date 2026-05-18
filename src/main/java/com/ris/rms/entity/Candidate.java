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
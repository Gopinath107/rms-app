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

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "phone_number", length = 50)
	private String phoneNumber;

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

	@Column(name = "degrees", length = 255)
	private String degrees;

	@Column(name = "specialization", length = 255)
	private String specialization;

	@Column(name = "yearofpassing")
	private Integer yearofpassing;

	@Column(name = "profilesummary", columnDefinition = "TEXT")
	private String profilesummary;

	@Column(name = "trainingsummary", columnDefinition = "TEXT")
	private String trainingsummary;

	@Column(name = "certificationsummary", columnDefinition = "TEXT")
	private String certificationsummary;

	@CreationTimestamp
	@Column(name = "createddt", updatable = false)
	private OffsetDateTime createddt;

	@Column(name = "updateddt")
	private OffsetDateTime updateddt;
}

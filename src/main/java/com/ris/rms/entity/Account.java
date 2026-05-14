package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "account", schema = "rms",
       uniqueConstraints = @UniqueConstraint(name = "uk_account_company_name",
                                             columnNames = {"company_id","account_name"}))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "company_id", nullable = false)
    private Long companyId; // FK to rms.company

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "contact_person_name", length = 255)
    private String contactPersonName;

    @Column(name = "contact_person_email", length = 255)
    private String contactPersonEmail;

    @Column(name = "relationship_start_date")
    private LocalDate relationshipStartDate;

    @Column(name = "relationship_end_date")
    private LocalDate relationshipEndDate;
    
 
    @Column(name = "status", nullable = false, length = 20)
    private String status; // "Active" | "Inactive"

}

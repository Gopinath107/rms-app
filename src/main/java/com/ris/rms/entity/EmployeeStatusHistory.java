package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter @Setter
@Entity
@Table(name = "employee_status_history", schema = "rms")
public class EmployeeStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id") private Long historyId;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "old_status", length = 50) private String oldStatus;
    @Column(name = "new_status", length = 50, nullable = false) private String newStatus;
    @Column(name = "changed_by_user_id") private Long changedByUserId;
    @Column(name = "changed_by_user_name", length = 255) private String changedByUserName;
    @CreationTimestamp @Column(name = "changed_at", updatable = false) private OffsetDateTime changedAt;
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "allocation_id") private Long allocationId;
}

package com.ris.rms.dto;
import lombok.Getter; import lombok.Setter;
import java.time.LocalDate; import java.time.OffsetDateTime;

@Getter @Setter
public class EmployeeStatusTransitionDto {
    // Request fields
    private String newStatus;
    private String reason;
    private LocalDate effectiveDate;
    private Long projectId;      // Required for Bench→Billable
    private LocalDate startDate; // For new allocation
    // Response fields
    private Long employeeId;
    private Long historyId;
    private String oldStatus;
    private String changedNewStatus;
    private OffsetDateTime changedAt;
    private String message;
}

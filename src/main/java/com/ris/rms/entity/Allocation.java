package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "allocation", schema = "rms")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "project_id", nullable = true)
    private Long projectId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "project_role", nullable = true)
    private String projectRole;

    @Column(name = "is_billable", nullable = false)
    private Boolean isBillable = Boolean.TRUE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status")
    private String status;
}

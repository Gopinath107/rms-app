package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "resource_request_approval", schema = "rms")
public class ResourceRequestApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "approver_id")
    private Long approverUserId; 

    @Column(name = "approver_role")
    private String approverRole;

    @Column(name = "status", nullable = false)
    private String status = "Pending";

    @Column(name = "comments")
    private String comments;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;
}

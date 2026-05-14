package com.ris.rms.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class GroupRequestView {

    private Long groupId;
    private String status;
    private LocalDate createdAt;
    private LocalDate approvedAt;
    private Long approvedByUserId;
    private String approvedByUserName;
    private String notes;

    private Long daysPendingToApprove;    
    private Long daysCompletedToApproved;

}

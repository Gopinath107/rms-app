package com.ris.rms.dto;

import java.util.List;

import lombok.Data;

@Data
public class DemandReportRequest {
    private Long userId;      
    private Long accountId;   
    private String fromDate;  
    private String toDate;   
    private List<String> toEmail;
    private List<String> ccEmail;
}
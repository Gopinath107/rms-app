package com.ris.rms.dto;

import lombok.Data;

@Data
public class EmployeeDocumentDto {
    private Long   documentId;
    private String documentType;    
    private String fileName;        
    private String url;             
    private String mimeType;
    private Long   sizeBytes;
    private String uploadedAt;     
    private Boolean isPrimary;
    private Integer version;
}

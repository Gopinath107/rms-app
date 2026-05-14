package com.ris.rms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StatusOptionDto {
    private String code;
    private String label;
    private Boolean active; 
}

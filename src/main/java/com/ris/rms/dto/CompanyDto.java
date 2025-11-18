package com.ris.rms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyDto {
    private Long companyId;

    @NotBlank(message = "companyName is required")
    @Size(max = 255, message = "companyName must be <= 255 chars")
    private String companyName;

    @Email(message = "companyEmail must be a valid email")
    @Size(max = 255, message = "companyEmail must be <= 255 chars")
    private String companyEmail;

    private String address;
}

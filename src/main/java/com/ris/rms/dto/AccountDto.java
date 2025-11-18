package com.ris.rms.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AccountDto {
	private Long accountId;

	@NotNull(message = "companyId is required")
	private Long companyId;

	private String companyName;

	@NotBlank(message = "accountName is required")
	@Size(max = 255, message = "accountName must be <= 255 chars")
	private String accountName;

	@Size(max = 100, message = "industry must be <= 100 chars")
	private String industry;

	@Size(max = 255, message = "contactPersonName must be <= 255 chars")
	private String contactPersonName;

	@Email(message = "contactPersonEmail must be a valid email")
	@Size(max = 255, message = "contactPersonEmail must be <= 255 chars")
	private String contactPersonEmail;

//	@NotNull(message = "relationshipStartDate is required")
	private LocalDate relationshipStartDate;

	private LocalDate relationshipEndDate;
	
	@Size(max = 20)
	private String status; 
}

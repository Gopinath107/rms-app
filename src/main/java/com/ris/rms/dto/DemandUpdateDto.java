package com.ris.rms.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

@Data
public class DemandUpdateDto {
	@NotBlank(message = "Demand Title is required")
	private String demandtitle;
	private String description;
	private String project_name;
	private String yearsofexp;
	private List<Long> skill_ids;
	private String roleduration;
	private String worklocpref;
	private String priority;
	private String location_type;
	private String work_mode;

	private String status;

	private LocalDate fulfilmentdt;
}
package com.ris.rms.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class DemandUpdateDto {
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
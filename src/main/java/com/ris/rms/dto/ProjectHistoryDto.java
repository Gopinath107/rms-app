package com.ris.rms.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectHistoryDto {
	private String projectName;
	private String clientName;
	private LocalDate startDate;
	private LocalDate endDate;
	private Long projectId;
	private Long accountId;

	public ProjectHistoryDto() {
	}

	public ProjectHistoryDto(String projectName, String clientName, LocalDate startDate, LocalDate endDate,
			Long projectId, Long accountId) {
		this.projectName = projectName;
		this.clientName = clientName;
		this.startDate = startDate;
		this.endDate = endDate;
		this.projectId = projectId;
		this.accountId = accountId;

	}

}

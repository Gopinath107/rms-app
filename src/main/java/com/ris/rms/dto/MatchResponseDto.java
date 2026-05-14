package com.ris.rms.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatchResponseDto {
	private Long employeeId;
	private String firstName;
	private String lastName;
	private String email;
	private String jobTitle;
	private String location;
	private Integer experienceYears;
	private String employmentType;
	private String status;

	private int matchScore;
	private String matchReasoning;
	private String scoringSource;
	private List<String> matchingSkills;
	private boolean hasResume;
}
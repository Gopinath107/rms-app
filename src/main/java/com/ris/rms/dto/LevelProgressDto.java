package com.ris.rms.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LevelProgressDto {
	private String level;
	private String status;

	private Long interviewerUserId;
	private String interviewerName;
	private String interviewerEmail;

	@JsonProperty("scheduledAt")
	private String scheduledAtText;

	@JsonIgnore
	private OffsetDateTime completedAt;

	@JsonProperty("completedAt")
	private String completedAtText;

	@JsonProperty("interviewNotes")
	private String interviewNotes;

	@JsonAlias({ "notes", "feedbackComments" })
	@JsonProperty("feedbackComments")
	private String feedback;

	private Integer rating;
	private String recommendation;
	private String comments;
}

package com.ris.rms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "interview_feedback", schema = "rms")
public class InterviewFeedback {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "feedback_id")
	private Long feedbackId;

	@Column(name = "interview_id", nullable = false)
	private Long interviewId;

	@Column(name = "rating")
	private Integer rating;

	@Column(name = "comments")
	private String comments;

	@Column(name = "recommendation", length = 50)
	private String recommendation;
}

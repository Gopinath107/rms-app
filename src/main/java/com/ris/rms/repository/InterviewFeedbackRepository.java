package com.ris.rms.repository;

import com.ris.rms.entity.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

	Optional<InterviewFeedback> findByInterviewId(Long interviewId);

	void deleteByInterviewId(Long interviewId);
}

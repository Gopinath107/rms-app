package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Interview;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

	List<Interview> findAllByRequestIdOrderByScheduledAtDesc(Long requestId);

	List<Interview> findAllByEmployeeIdOrderByScheduledAtDesc(Long employeeId);

	List<Interview> findAllByInterviewerIdOrderByScheduledAtDesc(Long interviewerUserId);

	List<Interview> findAllByRequestIdIn(List<Long> requestIds);

	Optional<Interview> findTopByRequestIdOrderByInterviewIdDesc(Long requestId);
}

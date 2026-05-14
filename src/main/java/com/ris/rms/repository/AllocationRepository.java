package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Allocation;

public interface AllocationRepository extends JpaRepository<Allocation, Long> {
	boolean existsByEmployeeIdAndProjectIdAndStatus(Long employeeId, Long projectId, String status);

	List<Allocation> findByEmployeeIdAndStatus(Long employeeId, String status);

	List<Allocation> findByCandidateIdAndStatus(Long candidateId, String status);

	Optional<Allocation> findByRequestId(Long requestId);

	Optional<Allocation> findFirstByEmployeeIdAndStatusInOrderByStartDateDesc(Long employeeId, List<String> statuses);

	List<Allocation> findByEmployeeIdAndStatusInOrderByStartDateDesc(Long employeeId, List<String> statuses);

	Optional<Allocation> findFirstByRequestIdOrderByStartDateDesc(Long requestId);

	List<Allocation> findByRequestIdIn(List<Long> requestIds);
}

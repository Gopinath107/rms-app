package com.ris.rms.repository;

import com.ris.rms.entity.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, Long> {
	
	List<ResourceRequest> findAllByGroupIdIn(List<Long> groupIds);
	
	List<ResourceRequest> findAllByDemandId(Long demandId);

	// Duplicate prevention: check if same employee already shared to same demand
	boolean existsByDemandIdAndEmployeeId(Long demandId, Long employeeId);

	// Duplicate prevention: check if same candidate already shared to same demand
	boolean existsByDemandIdAndCandidateId(Long demandId, Long candidateId);

	/**
	 * Returns only "real" resource requests for a demand — those created via resume sharing.
	 * Excludes old placeholder records that have no employee or candidate linked.
	 */
	@Query("SELECT rr FROM ResourceRequest rr WHERE rr.demandId = :demandId " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL)")
	List<ResourceRequest> findActualByDemandId(@Param("demandId") Long demandId);

	/** Count of real (resume-linked) requests for a demand */
	@Query("SELECT COUNT(rr) FROM ResourceRequest rr WHERE rr.demandId = :demandId " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL)")
	long countActualByDemandId(@Param("demandId") Long demandId);
}
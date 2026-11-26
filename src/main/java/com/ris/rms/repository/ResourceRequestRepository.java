package com.ris.rms.repository;

import com.ris.rms.entity.ResourceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, Long>, JpaSpecificationExecutor<ResourceRequest> {
	
	List<ResourceRequest> findAllByGroupIdIn(List<Long> groupIds);
	
	List<ResourceRequest> findAllByDemandId(Long demandId);

	// Duplicate prevention: check if same employee already shared to same demand
	boolean existsByDemandIdAndEmployeeId(Long demandId, Long employeeId);

	// Duplicate prevention: check if same candidate already shared to same demand
	boolean existsByDemandIdAndCandidateId(Long demandId, Long candidateId);

	Optional<ResourceRequest> findFirstByDemandIdAndCandidateId(Long demandId, Long candidateId);

	Optional<ResourceRequest> findFirstByDemandIdAndEmployeeId(Long demandId, Long employeeId);

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

	/** Total submission events for a specific date (e.g. today or yesterday) */
	@Query("SELECT COUNT(rr) FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL) AND rr.submittedDate = :date")
	long countSubmissionsByDate(@Param("date") LocalDate date);

	/** Unique resources submitted for a specific date */
	@Query("SELECT COUNT(DISTINCT CONCAT(rr.resourceType, '_', COALESCE(rr.employeeId, rr.candidateId))) " +
	       "FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL) AND rr.submittedDate = :date")
	long countUniqueResourcesByDate(@Param("date") LocalDate date);

	/** Total all-time submission events across demands */
	@Query("SELECT COUNT(rr) FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL)")
	long countTotalActualSubmissions();

	/** Total all-time unique resources submitted across demands */
	@Query("SELECT COUNT(DISTINCT CONCAT(rr.resourceType, '_', COALESCE(rr.employeeId, rr.candidateId))) " +
	       "FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL)")
	long countTotalUniqueActualResources();

	/** Recent real submissions ordered newest first */
	@Query("SELECT rr FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL) " +
	       "ORDER BY rr.submittedDate DESC, rr.requestId DESC")
	List<ResourceRequest> findRecentActualSubmissions(Pageable pageable);

	/** Paginated real submissions */
	@Query("SELECT rr FROM ResourceRequest rr WHERE rr.demandId IS NOT NULL " +
	       "AND (rr.employeeId IS NOT NULL OR rr.candidateId IS NOT NULL)")
	Page<ResourceRequest> findAllActualSubmissions(Pageable pageable);
}

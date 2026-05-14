package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ris.rms.entity.CandidateDocument;

@Repository
public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, Long> {

	@Query("""
			    select d
			    from CandidateDocument d
			    where d.candidateId = :candidateId
			      and d.isPrimary = true
			      and (d.documentType is null or lower(d.documentType) = 'resume')
			""")
	Optional<CandidateDocument> findPrimaryResume(@Param("candidateId") Long candidateId);

	@Query("""
			    select d
			    from CandidateDocument d
			    where d.candidateId in :candidateIds
			      and d.isPrimary = true
			      and (d.documentType is null or lower(d.documentType) = 'resume')
			""")
	List<CandidateDocument> findPrimaryResumesForCandidates(@Param("candidateIds") List<Long> candidateIds);
}

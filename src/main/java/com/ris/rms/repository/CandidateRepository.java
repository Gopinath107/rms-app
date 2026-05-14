package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ris.rms.entity.Candidate;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    boolean existsByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

    Optional<Candidate> findByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

    List<Candidate> findAllByCompanyId(Long companyId);
}

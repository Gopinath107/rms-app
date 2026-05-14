package com.ris.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ris.rms.entity.CandidateSkill;
import com.ris.rms.entity.CandidateSkillId;

@Repository
public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, CandidateSkillId> {

	List<CandidateSkill> findAllByCandidateId(Long candidateId);

	void deleteAllByCandidateId(Long candidateId);

	List<CandidateSkill> findAllByCandidateIdIn(List<Long> candidateIds);

}

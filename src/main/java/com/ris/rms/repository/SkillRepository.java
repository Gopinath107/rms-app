package com.ris.rms.repository;

import com.ris.rms.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {
	boolean existsBySkillNameIgnoreCase(String skillName);

	Optional<Skill> findBySkillNameIgnoreCase(String skillName);

	List<Skill> findAllByOrderBySkillNameAsc();
}

package com.ris.rms.repository;

import com.ris.rms.entity.ProjectSkill;
import com.ris.rms.entity.ProjectSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSkillRepository extends JpaRepository<ProjectSkill, ProjectSkillId> {
	List<ProjectSkill> findAllByProjectId(Long projectId);

	void deleteAllByProjectId(Long projectId);
}

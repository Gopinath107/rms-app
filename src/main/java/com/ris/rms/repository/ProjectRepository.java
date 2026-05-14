package com.ris.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {
	boolean existsByCompanyIdAndProjectNameIgnoreCase(Long companyId, String projectName);

	List<Project> findAllByCompanyId(Long companyId);

	List<Project> findAllByCompanyIdAndAccountId(Long companyId, Long accountId);
	
}

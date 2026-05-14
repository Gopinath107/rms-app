package com.ris.rms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ris.rms.entity.ResourceRequestGroup;

public interface ResReqGroupRepository
		extends JpaRepository<ResourceRequestGroup, Long>, JpaSpecificationExecutor<ResourceRequestGroup> {

	List<ResourceRequestGroup> findAllByCompanyId(Long companyId);

	List<ResourceRequestGroup> findAllByProjectId(Long projectId);

	List<ResourceRequestGroup> findAllByCompanyIdAndProjectId(Long companyId, Long projectId);
}

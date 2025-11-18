package com.ris.rms.repository;

import com.ris.rms.entity.ResourceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResourceRequestRepository extends JpaRepository<ResourceRequest, Long> {
	
	List<ResourceRequest> findAllByGroupIdIn(List<Long> groupIds);
	
	List<ResourceRequest> findAllByDemandId(Long demandId);
}
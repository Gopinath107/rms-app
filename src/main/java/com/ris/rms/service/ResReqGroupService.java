package com.ris.rms.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ris.rms.dto.BulkCreateResReqDto;
import com.ris.rms.dto.BulkCreateResponseDto;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.dto.ResourceRequestGroupDto;

public interface ResReqGroupService {
	BulkCreateResponseDto bulkCreate(BulkCreateResReqDto dto);
	List<ResourceRequestGroupDto> list(Long companyId, Long projectId, Integer page, Integer size);
	ResourceRequestGroupDto getById(Long groupId);
	void recomputeGroupStatus(Long groupId);
	void saveResponseIntoTemplate(Long groupId, Map<String, Object> response);
	
	Page<GroupFlowDto> getGroupFlowList(
            Long companyId, Long accountId, Long projectId, Long groupId,
            String fromDate, String toDate, Pageable pageable
    );

}

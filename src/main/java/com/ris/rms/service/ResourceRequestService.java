package com.ris.rms.service;

import com.ris.rms.dto.ResourceRequestDto;

import java.util.List;

public interface ResourceRequestService {
	ResourceRequestDto create(ResourceRequestDto dto);

	ResourceRequestDto getById(Long id);

	List<ResourceRequestDto> list(Long companyId, Long projectId, Long groupId, String status, String priority,
			String q, Integer page, Integer size);

	ResourceRequestDto update(Long id, ResourceRequestDto dto);

	void delete(Long id);

}

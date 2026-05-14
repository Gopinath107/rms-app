package com.ris.rms.service;

import com.ris.rms.dto.ProjectDto;

import java.util.List;

public interface ProjectService {
	ProjectDto create(ProjectDto dto);

	ProjectDto getById(Long id);

	List<ProjectDto> list(Long companyId, Long accountId, Long managerUserId, String status, String priority, String q,
			Integer page, Integer size);

	ProjectDto update(Long id, ProjectDto dto);

	void delete(Long id);
}

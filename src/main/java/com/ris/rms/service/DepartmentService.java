package com.ris.rms.service;

import com.ris.rms.dto.DepartmentDto;

import java.util.List;

public interface DepartmentService {
	DepartmentDto create(DepartmentDto dto);

	DepartmentDto getById(Long id);

	List<DepartmentDto> list(Long companyId, String q, Integer page, Integer size);

	DepartmentDto update(Long id, DepartmentDto dto);

	void delete(Long id);
}

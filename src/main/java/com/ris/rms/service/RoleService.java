package com.ris.rms.service;

import com.ris.rms.dto.RoleDto;
import java.util.List;

public interface RoleService {
	RoleDto create(RoleDto dto);

	RoleDto getById(Long id);

	List<RoleDto> list(Long companyId, String q, Integer page, Integer size);

	RoleDto update(Long id, RoleDto dto);

	void delete(Long id);
}

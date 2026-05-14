package com.ris.rms.service;

import com.ris.rms.dto.UserAccountDto;

import java.util.List;

public interface UserAccountService {
	UserAccountDto create(UserAccountDto dto);

	UserAccountDto getById(Long id);

	List<UserAccountDto> list(Long companyId, Long roleId, Boolean isActive, String q, Integer page, Integer size);

	UserAccountDto update(Long id, UserAccountDto dto);

	void delete(Long id);
}

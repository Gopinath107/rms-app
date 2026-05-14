package com.ris.rms.service;

import org.springframework.data.domain.Page;

import com.ris.rms.dto.AccountDto;

public interface AccountService {
	AccountDto create(AccountDto dto);

	AccountDto getById(Long id);

	Page<AccountDto> list(Long companyId, String q, Integer page, Integer size);

	AccountDto update(Long id, AccountDto dto);

	void delete(Long id);
}

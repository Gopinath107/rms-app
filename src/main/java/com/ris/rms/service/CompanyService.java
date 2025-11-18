package com.ris.rms.service;

import com.ris.rms.dto.CompanyDto;

import java.util.List;

public interface CompanyService {
	CompanyDto create(CompanyDto dto);

	CompanyDto getById(Long id);

	List<CompanyDto> list(String q, Integer page, Integer size);

	CompanyDto update(Long id, CompanyDto dto);

	void delete(Long id);
}

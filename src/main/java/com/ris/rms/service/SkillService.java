package com.ris.rms.service;

import com.ris.rms.dto.SkillDto;

import java.util.List;

public interface SkillService {
	SkillDto create(SkillDto dto);

	SkillDto getById(Long id);

	List<SkillDto> list(String q, Integer page, Integer size);

	SkillDto update(Long id, SkillDto dto);

	void delete(Long id);
}

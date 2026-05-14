package com.ris.rms.service;

import com.ris.rms.dto.StatusOptionDto;

import java.util.List;
import java.util.Map;

public interface StatusMetaService {
	List<StatusOptionDto> getByCategory(String category);

	Map<String, List<StatusOptionDto>> getAllActiveGrouped();
}

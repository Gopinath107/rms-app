package com.ris.rms.service.impl;

import com.ris.rms.dto.StatusOptionDto;
import com.ris.rms.entity.StatusMaster;
import com.ris.rms.repository.StatusMasterRepository;
import com.ris.rms.service.StatusMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatusMetaServiceImpl implements StatusMetaService {

	private final StatusMasterRepository repo;

	@Override
	public List<StatusOptionDto> getByCategory(String category) {
		if (category == null || category.isBlank()) {
			throw new IllegalArgumentException("category is required");
		}
		return repo.findByCategoryAndIsActiveTrueOrderByStatusIdAsc(category.trim()).stream().map(this::toDto)
				.toList();
	}

	@Override
	public Map<String, List<StatusOptionDto>> getAllActiveGrouped() {
		var rows = repo.findByIsActiveTrueOrderByCategoryAscStatusIdAsc();
		return rows.stream().map(this::toDtoEntry).collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
				Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
	}

	private StatusOptionDto toDto(StatusMaster s) {
		return StatusOptionDto.builder().code(s.getCode()).label(s.getLabel())
				.active(s.getIsActive()).build();
	}

	private Map.Entry<String, StatusOptionDto> toDtoEntry(StatusMaster s) {
		return Map.entry(s.getCategory(), toDto(s));
	}
}

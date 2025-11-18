package com.ris.rms.service.impl;

import com.ris.rms.dto.CompanyDto;
import com.ris.rms.entity.Company;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyServiceImpl implements CompanyService {

	private final CompanyRepository repo;

	@Override
	public CompanyDto create(CompanyDto dto) {

		if (repo.existsByCompanyNameIgnoreCase(dto.getCompanyName())) {
			throw new IllegalArgumentException("Company name already exists");
		}
		Company entity = toEntity(dto);
		entity.setCompanyId(null);
		Company saved = repo.save(entity);
		return toDto(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public CompanyDto getById(Long id) {
		Company c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Company not found"));
		return toDto(c);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CompanyDto> list(String q, Integer page, Integer size) {
		if (page != null && size != null && page >= 0 && size > 0) {
			if (q != null && !q.isBlank()) {
				return repo.findAll(PageRequest.of(page, size)).stream().map(this::toDto)
						.filter(d -> d.getCompanyName().toLowerCase().contains(q.toLowerCase())).toList();
			}
			return repo.findAll(PageRequest.of(page, size)).stream().map(this::toDto).toList();
		}

		List<Company> all = repo.findAll();
		if (q != null && !q.isBlank()) {
			String needle = q.toLowerCase();
			return all.stream().map(this::toDto).filter(d -> d.getCompanyName().toLowerCase().contains(needle))
					.toList();
		}
		return all.stream().map(this::toDto).toList();
	}

	@Override
	public CompanyDto update(Long id, CompanyDto dto) {
		Company existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (dto.getCompanyName() != null && !dto.getCompanyName().equalsIgnoreCase(existing.getCompanyName())
				&& repo.existsByCompanyNameIgnoreCase(dto.getCompanyName())) {
			throw new IllegalArgumentException("Company name already exists");
		}

		if (dto.getCompanyName() != null)
			existing.setCompanyName(dto.getCompanyName());
		if (dto.getCompanyEmail() != null)
			existing.setCompanyEmail(dto.getCompanyEmail());
		if (dto.getAddress() != null)
			existing.setAddress(dto.getAddress());

		Company saved = repo.save(existing);
		return toDto(saved);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id)) {
			throw new IllegalArgumentException("Company not found");
		}
		repo.deleteById(id);
	}

	private CompanyDto toDto(Company c) {
		CompanyDto dto = new CompanyDto();
		dto.setCompanyId(c.getCompanyId());
		dto.setCompanyName(c.getCompanyName());
		dto.setCompanyEmail(c.getCompanyEmail());
		dto.setAddress(c.getAddress());
		return dto;
	}

	private Company toEntity(CompanyDto dto) {
		Company c = new Company();
		c.setCompanyId(dto.getCompanyId());
		c.setCompanyName(dto.getCompanyName());
		c.setCompanyEmail(dto.getCompanyEmail());
		c.setAddress(dto.getAddress());
		return c;
	}
}

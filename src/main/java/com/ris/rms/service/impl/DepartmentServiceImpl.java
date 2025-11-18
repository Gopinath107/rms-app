package com.ris.rms.service.impl;

import com.ris.rms.dto.DepartmentDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Department;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentServiceImpl implements DepartmentService {

	private final DepartmentRepository repo;
	private final CompanyRepository companyRepo;

	@Override
	public DepartmentDto create(DepartmentDto dto) {

		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (repo.existsByCompanyIdAndDepartmentNameIgnoreCase(dto.getCompanyId(), dto.getDepartmentName())) {
			throw new IllegalArgumentException("Department name already exists for this company");
		}

		if (dto.getParentDepartmentId() != null) {
			Department parent = repo.findById(dto.getParentDepartmentId())
					.orElseThrow(() -> new IllegalArgumentException("Parent department not found"));
			if (!parent.getCompanyId().equals(dto.getCompanyId())) {
				throw new IllegalArgumentException("Parent department must belong to the same company");
			}
		}

		Department entity = toEntity(dto);
		entity.setDepartmentId(null);
		Department saved = repo.save(entity);
		return toDto(saved, comp.getCompanyName());
	}

	@Override
	@Transactional(readOnly = true)
	public DepartmentDto getById(Long id) {
		Department d = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));
		String companyName = companyRepo.findById(d.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(d, companyName);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DepartmentDto> list(Long companyId, String q, Integer page, Integer size) {
		List<Department> base;
		if (companyId != null) {
			base = repo.findAllByCompanyId(companyId);
		} else {
			base = (page != null && size != null && page >= 0 && size > 0)
					? repo.findAll(PageRequest.of(page, size)).getContent()
					: repo.findAll();
		}

		return base.stream()
				.filter(d -> q == null || q.isBlank() || d.getDepartmentName().toLowerCase().contains(q.toLowerCase()))
				.map(d -> {
					String companyName = companyRepo.findById(d.getCompanyId()).map(Company::getCompanyName)
							.orElse(null);
					return toDto(d, companyName);
				}).toList();
	}

	@Override
	public DepartmentDto update(Long id, DepartmentDto dto) {
		Department existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Department not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}

		if (dto.getDepartmentName() != null && !dto.getDepartmentName().equalsIgnoreCase(existing.getDepartmentName())
				&& repo.existsByCompanyIdAndDepartmentNameIgnoreCase(existing.getCompanyId(),
						dto.getDepartmentName())) {
			throw new IllegalArgumentException("Department name already exists for this company");
		}

		if (dto.getParentDepartmentId() != null) {
			if (dto.getParentDepartmentId().equals(existing.getDepartmentId())) {
				throw new IllegalArgumentException("parentDepartmentId cannot be same as departmentId");
			}
			Department parent = repo.findById(dto.getParentDepartmentId())
					.orElseThrow(() -> new IllegalArgumentException("Parent department not found"));
			if (!parent.getCompanyId().equals(existing.getCompanyId())) {
				throw new IllegalArgumentException("Parent department must belong to the same company");
			}
		}

		if (dto.getDepartmentName() != null)
			existing.setDepartmentName(dto.getDepartmentName());
		if (dto.getParentDepartmentId() != null || dto.getParentDepartmentId() == null) {

			existing.setParentDepartmentId(dto.getParentDepartmentId());
		}

		Department saved = repo.save(existing);
		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(saved, companyName);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id)) {
			throw new IllegalArgumentException("Department not found");
		}
		repo.deleteById(id);
	}

	private DepartmentDto toDto(Department d, String companyName) {
		DepartmentDto dto = new DepartmentDto();
		dto.setDepartmentId(d.getDepartmentId());
		dto.setCompanyId(d.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setDepartmentName(d.getDepartmentName());
		dto.setParentDepartmentId(d.getParentDepartmentId());
		return dto;
	}

	private Department toEntity(DepartmentDto dto) {
		Department d = new Department();
		d.setDepartmentId(dto.getDepartmentId());
		d.setCompanyId(dto.getCompanyId());
		d.setDepartmentName(dto.getDepartmentName());
		d.setParentDepartmentId(dto.getParentDepartmentId());
		return d;
	}
}

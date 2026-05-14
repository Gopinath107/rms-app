package com.ris.rms.service.impl;

import com.ris.rms.dto.RoleDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Role;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

	private final RoleRepository repo;
	private final CompanyRepository companyRepo;

	@Override
	public RoleDto create(RoleDto dto) {

		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (repo.existsByCompanyIdAndRoleNameIgnoreCase(dto.getCompanyId(), dto.getRoleName())) {
			throw new IllegalArgumentException("Role name already exists for this company");
		}

		Role entity = toEntity(dto);
		entity.setRoleId(null);
		Role saved = repo.save(entity);

		RoleDto out = toDto(saved, comp.getCompanyName());
		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public RoleDto getById(Long id) {
		Role r = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
		String companyName = companyRepo.findById(r.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(r, companyName);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoleDto> list(Long companyId, String q, Integer page, Integer size) {
		List<Role> base;
		if (companyId != null) {
			base = repo.findAllByCompanyId(companyId);
		} else {
			base = (page != null && size != null && page >= 0 && size > 0)
					? repo.findAll(PageRequest.of(page, size)).getContent()
					: repo.findAll();
		}

		return base.stream()
				.filter(r -> q == null || q.isBlank() || r.getRoleName().toLowerCase().contains(q.toLowerCase()))
				.map(r -> {
					String companyName = companyRepo.findById(r.getCompanyId()).map(Company::getCompanyName)
							.orElse(null);
					return toDto(r, companyName);
				}).toList();
	}

	@Override
	public RoleDto update(Long id, RoleDto dto) {
		Role existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}

		if (dto.getRoleName() != null && !dto.getRoleName().equalsIgnoreCase(existing.getRoleName())
				&& repo.existsByCompanyIdAndRoleNameIgnoreCase(existing.getCompanyId(), dto.getRoleName())) {
			throw new IllegalArgumentException("Role name already exists for this company");
		}

		if (dto.getRoleName() != null)
			existing.setRoleName(dto.getRoleName());

		Role saved = repo.save(existing);
		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(saved, companyName);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id)) {
			throw new IllegalArgumentException("Role not found");
		}
		repo.deleteById(id);
	}

	private RoleDto toDto(Role r, String companyName) {
		RoleDto dto = new RoleDto();
		dto.setRoleId(r.getRoleId());
		dto.setCompanyId(r.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setRoleName(r.getRoleName());
		return dto;
	}

	private Role toEntity(RoleDto dto) {
		Role r = new Role();
		r.setRoleId(dto.getRoleId());
		r.setCompanyId(dto.getCompanyId());
		r.setRoleName(dto.getRoleName());
		return r;
	}
}

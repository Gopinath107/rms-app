package com.ris.rms.service.impl;

import com.ris.rms.dto.UserAccountDto;
import com.ris.rms.entity.*;
import com.ris.rms.repository.*;
import com.ris.rms.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAccountServiceImpl implements UserAccountService {

	private final UserAccountRepository repo;
	private final CompanyRepository companyRepo;
	private final EmployeeRepository employeeRepo;
	private final RoleRepository roleRepo;

	@Override
	public UserAccountDto create(UserAccountDto dto) {
		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		Employee emp = employeeRepo.findById(dto.getEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException("Employee not found"));

		if (!Objects.equals(emp.getCompanyId(), dto.getCompanyId())) {
			throw new IllegalArgumentException("Employee must belong to the same company");
		}

		Role role = roleRepo.findById(dto.getRoleId())
				.orElseThrow(() -> new IllegalArgumentException("Role not found"));

		if (!Objects.equals(role.getCompanyId(), dto.getCompanyId())) {
			throw new IllegalArgumentException("Role must belong to the same company");
		}

		if (repo.existsByEmployeeId(dto.getEmployeeId())) {
			throw new IllegalArgumentException("User already exists for this employee");
		}

		if (repo.existsByEmailIgnoreCase(dto.getEmail())) {
			throw new IllegalArgumentException("Email already in use");
		}

		if (dto.getPasswordHash() == null || dto.getPasswordHash().isBlank()) {
			throw new IllegalArgumentException("passwordHash is required");
		}

		UserAccount ua = new UserAccount();
		ua.setUserId(null);
		ua.setCompanyId(dto.getCompanyId());
		ua.setEmployeeId(dto.getEmployeeId());
		ua.setRoleId(dto.getRoleId());
		ua.setEmail(dto.getEmail());
		ua.setPasswordHash(dto.getPasswordHash());
		ua.setIsActive(dto.getIsActive() == null ? Boolean.TRUE : dto.getIsActive());

		UserAccount saved = repo.save(ua);
		return toDto(saved, comp.getCompanyName(), emp.getFirstName() + " " + emp.getLastName(), role.getRoleName());
	}

	@Override
	@Transactional(readOnly = true)
	public UserAccountDto getById(Long id) {
		UserAccount ua = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("User account not found"));

		String companyName = companyRepo.findById(ua.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String employeeName = employeeRepo.findById(ua.getEmployeeId())
				.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
		String roleName = roleRepo.findById(ua.getRoleId()).map(Role::getRoleName).orElse(null);

		return toDto(ua, companyName, employeeName, roleName);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserAccountDto> list(Long companyId, Long roleId, Boolean isActive, String q, Integer page,
			Integer size) {
		List<UserAccount> base;
		if (companyId != null && roleId != null) {
			base = repo.findAllByCompanyIdAndRoleId(companyId, roleId);
		} else if (companyId != null) {
			base = repo.findAllByCompanyId(companyId);
		} else {
			base = (page != null && size != null && page >= 0 && size > 0)
					? repo.findAll(PageRequest.of(page, size)).getContent()
					: repo.findAll();
		}

		final String needle = q == null ? null : q.toLowerCase();
		return base.stream().filter(u -> isActive == null || u.getIsActive().equals(isActive))
				.filter(u -> needle == null || (u.getEmail() != null && u.getEmail().toLowerCase().contains(needle)))
				.map(u -> {
					String companyName = companyRepo.findById(u.getCompanyId()).map(Company::getCompanyName)
							.orElse(null);
					String employeeName = employeeRepo.findById(u.getEmployeeId())
							.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
					String roleName = roleRepo.findById(u.getRoleId()).map(Role::getRoleName).orElse(null);
					return toDto(u, companyName, employeeName, roleName);
				}).toList();
	}

	@Override
	public UserAccountDto update(Long id, UserAccountDto dto) {
		UserAccount existing = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("User account not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}
		if (dto.getEmployeeId() != null && !dto.getEmployeeId().equals(existing.getEmployeeId())) {
			throw new IllegalArgumentException("employeeId cannot be changed");
		}

		if (dto.getRoleId() != null && !dto.getRoleId().equals(existing.getRoleId())) {
			Role role = roleRepo.findById(dto.getRoleId())
					.orElseThrow(() -> new IllegalArgumentException("Role not found"));
			if (!role.getCompanyId().equals(existing.getCompanyId())) {
				throw new IllegalArgumentException("Role must belong to the same company");
			}
			existing.setRoleId(dto.getRoleId());
		}

		if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(existing.getEmail())
				&& repo.existsByEmailIgnoreCase(dto.getEmail())) {
			throw new IllegalArgumentException("Email already in use");
		}
		if (dto.getEmail() != null)
			existing.setEmail(dto.getEmail());

		if (dto.getPasswordHash() != null && !dto.getPasswordHash().isBlank()) {
			existing.setPasswordHash(dto.getPasswordHash());
		}

		if (dto.getIsActive() != null)
			existing.setIsActive(dto.getIsActive());

		UserAccount saved = repo.save(existing);

		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String employeeName = employeeRepo.findById(saved.getEmployeeId())
				.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
		String roleName = roleRepo.findById(saved.getRoleId()).map(Role::getRoleName).orElse(null);

		return toDto(saved, companyName, employeeName, roleName);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("User account not found");
		repo.deleteById(id);
	}

	private UserAccountDto toDto(UserAccount u, String companyName, String employeeName, String roleName) {
		UserAccountDto dto = new UserAccountDto();
		dto.setUserId(u.getUserId());
		dto.setCompanyId(u.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setEmployeeId(u.getEmployeeId());
		dto.setEmployeeName(employeeName);
		dto.setRoleId(u.getRoleId());
		dto.setRoleName(roleName);
		dto.setEmail(u.getEmail());
		dto.setIsActive(u.getIsActive());

		return dto;
	}
}

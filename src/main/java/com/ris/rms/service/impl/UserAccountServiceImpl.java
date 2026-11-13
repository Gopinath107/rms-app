package com.ris.rms.service.impl;

import com.ris.rms.dto.UserAccountDto;
import com.ris.rms.entity.*;
import com.ris.rms.repository.*;
import com.ris.rms.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

		Optional<UserAccount> existingOpt = repo.findByEmployeeId(dto.getEmployeeId());
		UserAccount saved;

		if (existingOpt.isPresent()) {
			UserAccount existing = existingOpt.get();

			if (!Objects.equals(existing.getCompanyId(), dto.getCompanyId())) {
				throw new IllegalArgumentException("User exists but belongs to a different company");
			}

			if (existing.getRoleIds() == null) {
				existing.setRoleIds(new ArrayList<>());
			}
			if (!existing.getRoleIds().contains(dto.getRoleId())) {
				existing.getRoleIds().add(dto.getRoleId());
			}

			if (dto.getIsActive() != null) {
				existing.setIsActive(dto.getIsActive());
			}

			saved = repo.save(existing);
		
		} else {
			
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
			
			List<Long> roles = new ArrayList<>();
			roles.add(dto.getRoleId());
			ua.setRoleIds(roles);
			
			ua.setEmail(dto.getEmail());
			ua.setPasswordHash(dto.getPasswordHash());
			ua.setIsActive(dto.getIsActive() == null ? Boolean.TRUE : dto.getIsActive());

			saved = repo.save(ua);
		}

		
		UserAccountDto responseDto = toDto(saved, comp.getCompanyName(), emp.getFirstName() + " " + emp.getLastName(), null);
		responseDto.setRoleId(dto.getRoleId());
		responseDto.setRoleName(role.getRoleName());
		
		return responseDto;
	}

	@Override
	@Transactional(readOnly = true)
	public UserAccountDto getById(Long id) {
		UserAccount ua = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("User account not found"));

		String companyName = resolveCompanyName(ua.getCompanyId());
		String employeeName = resolveEmployeeName(ua.getEmployeeId());
		
		String roleName = null;
		if (ua.getRoleId() != null) {
			roleName = roleRepo.findById(ua.getRoleId()).map(Role::getRoleName).orElse(null);
		}

		return toDto(ua, companyName, employeeName, roleName);
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserAccountDto> list(Long companyId, Long roleId, Boolean isActive, String q, Integer page,
			Integer size) {
		
		List<UserAccount> base;
		if (companyId != null) {
			base = repo.findAllByCompanyId(companyId);
		} else {
			base = (page != null && size != null && page >= 0 && size > 0)
					? repo.findAll(PageRequest.of(page, size)).getContent()
					: repo.findAll();
		}

        Map<Long, String> roleMap = roleRepo.findAll().stream()
                .collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));

		final String needle = q == null ? null : q.toLowerCase();
		
		return base.stream()
				.filter(u -> isActive == null || Objects.equals(u.getIsActive(), isActive))
				.filter(u -> needle == null || (u.getEmail() != null && u.getEmail().toLowerCase().contains(needle)))
				
				.flatMap(u -> {
                    List<Long> rIds = u.getRoleIds();
                    
                    if (rIds == null || rIds.isEmpty()) {
                        return Stream.of(toDto(u,
                        		resolveCompanyName(u.getCompanyId()),
                        		resolveEmployeeName(u.getEmployeeId()),
                        		null));
                    }

                    return rIds.stream().map(rId -> {
                    	String cName = resolveCompanyName(u.getCompanyId());
                    	String eName = resolveEmployeeName(u.getEmployeeId());
                        String rName = roleMap.getOrDefault(rId, "Unknown Role");
                        
                        UserAccountDto dto = new UserAccountDto();
                		dto.setUserId(u.getUserId());
                		dto.setCompanyId(u.getCompanyId());
                		dto.setCompanyName(cName);
                		dto.setEmployeeId(u.getEmployeeId());
                		dto.setEmployeeName(eName);
                		
                		dto.setRoleId(rId);
                		dto.setRoleName(rName);
                		
                		dto.setEmail(u.getEmail());
                		dto.setIsActive(u.getIsActive());
                        return dto;
                    });
                })
				.filter(dto -> roleId == null || (dto.getRoleId() != null && dto.getRoleId().equals(roleId)))
				.toList();
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
		
		String updatedRoleName = null;
		Long updatedRoleId = null;

		if (dto.getRoleId() != null) {
			Role role = roleRepo.findById(dto.getRoleId())
					.orElseThrow(() -> new IllegalArgumentException("Role not found"));
			if (!role.getCompanyId().equals(existing.getCompanyId())) {
				throw new IllegalArgumentException("Role must belong to the same company");
			}
			
			updatedRoleName = role.getRoleName();
			updatedRoleId = role.getRoleId();
			
			
			List<Long> currentRoles = existing.getRoleIds();
			if (currentRoles == null) {
				currentRoles = new ArrayList<>();
			}
			
			if (currentRoles.isEmpty()) {
				currentRoles.add(dto.getRoleId());
			} else {
			
				currentRoles.remove(dto.getRoleId());
				
				currentRoles.add(0, dto.getRoleId());
			}
			existing.setRoleIds(currentRoles);
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

		String companyName = resolveCompanyName(saved.getCompanyId());
		String employeeName = resolveEmployeeName(saved.getEmployeeId());
		
		if (updatedRoleId == null && saved.getRoleId() != null) {
			updatedRoleId = saved.getRoleId();
			updatedRoleName = roleRepo.findById(updatedRoleId).map(Role::getRoleName).orElse(null);
		}

		UserAccountDto response = toDto(saved, companyName, employeeName, null);
		response.setRoleId(updatedRoleId);
		response.setRoleName(updatedRoleName);
		
		return response;
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

	private String resolveCompanyName(Long companyId) {
		if (companyId == null) {
			return null;
		}
		return companyRepo.findById(companyId).map(Company::getCompanyName).orElse(null);
	}

	private String resolveEmployeeName(Long employeeId) {
		if (employeeId == null) {
			return null;
		}
		return employeeRepo.findById(employeeId).map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
	}
}
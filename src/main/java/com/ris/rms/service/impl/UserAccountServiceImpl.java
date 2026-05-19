package com.ris.rms.service.impl;

import com.ris.rms.dto.UserAccountDto;
import com.ris.rms.entity.*;
import com.ris.rms.repository.*;
import com.ris.rms.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAccountServiceImpl implements UserAccountService {

	private final UserAccountRepository repo;
	private final CompanyRepository companyRepo;
	private final EmployeeRepository employeeRepo;
	private final RoleRepository roleRepo;

	// ── Create ────────────────────────────────────────────────────────────────

	@Override
	public UserAccountDto create(UserAccountDto dto) {
		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		Employee emp = employeeRepo.findById(dto.getEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException("Employee not found"));

		if (!Objects.equals(emp.getCompanyId(), dto.getCompanyId())) {
			throw new IllegalArgumentException("Employee must belong to the same company");
		}

		// Resolve role IDs — accept list or single
		List<Long> requestedRoleIds = resolveRoleIds(dto);
		if (requestedRoleIds.isEmpty()) {
			throw new IllegalArgumentException("At least one role is required");
		}
		validateRoles(requestedRoleIds, dto.getCompanyId());

		Optional<UserAccount> existingOpt = repo.findByEmployeeId(dto.getEmployeeId());
		UserAccount saved;

		if (existingOpt.isPresent()) {
			// User already exists — merge new roles in
			UserAccount existing = existingOpt.get();

			if (!Objects.equals(existing.getCompanyId(), dto.getCompanyId())) {
				throw new IllegalArgumentException("User exists but belongs to a different company");
			}

			if (existing.getRoleIds() == null) {
				existing.setRoleIds(new ArrayList<>());
			}
			for (Long rId : requestedRoleIds) {
				if (!existing.getRoleIds().contains(rId)) {
					existing.getRoleIds().add(rId);
				}
			}

			if (dto.getIsActive() != null) {
				existing.setIsActive(dto.getIsActive());
			}
			saved = repo.save(existing);

		} else {
			// Brand-new user account
			if (repo.existsByEmailIgnoreCase(dto.getEmail())) {
				throw new IllegalArgumentException("Email already in use");
			}
			if (dto.getPasswordHash() == null || dto.getPasswordHash().isBlank()) {
				throw new IllegalArgumentException("passwordHash is required for new accounts");
			}

			UserAccount ua = new UserAccount();
			ua.setUserId(null);
			ua.setCompanyId(dto.getCompanyId());
			ua.setEmployeeId(dto.getEmployeeId());
			ua.setRoleIds(new ArrayList<>(requestedRoleIds));
			ua.setEmail(dto.getEmail());
			ua.setPasswordHash(dto.getPasswordHash());
			ua.setIsActive(dto.getIsActive() == null ? Boolean.TRUE : dto.getIsActive());

			saved = repo.save(ua);
		}

		Map<Long, String> roleMap = buildRoleMap(saved.getRoleIds());
		String companyName = comp.getCompanyName();
		String empName = emp.getFirstName() + " " + emp.getLastName();
		return toDto(saved, companyName, empName, roleMap);
	}

	// ── Get by ID ─────────────────────────────────────────────────────────────

	@Override
	@Transactional(readOnly = true)
	public UserAccountDto getById(Long id) {
		UserAccount ua = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("User account not found"));

		String companyName = companyRepo.findById(ua.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String employeeName = employeeRepo.findById(ua.getEmployeeId())
				.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
		Map<Long, String> roleMap = buildRoleMap(ua.getRoleIds());

		return toDto(ua, companyName, employeeName, roleMap);
	}

	// ── List (one row per USER, not per role) ─────────────────────────────────

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

		// Build a single role name map for all roles at once (avoid N+1 per user)
		Map<Long, String> roleNameMap = roleRepo.findAll().stream()
				.collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));

		// Pre-load company/employee names keyed by ID to avoid N+1 per user
		Map<Long, String> companyNameMap = companyRepo.findAll().stream()
				.collect(Collectors.toMap(Company::getCompanyId, Company::getCompanyName));
		Map<Long, String> employeeNameMap = employeeRepo.findAll().stream()
				.collect(Collectors.toMap(
						e -> e.getEmployeeId(),
						e -> e.getFirstName() + " " + e.getLastName()));

		final String needle = q == null ? null : q.toLowerCase();

		return base.stream()
				.filter(u -> isActive == null || u.getIsActive().equals(isActive))
				.filter(u -> needle == null
						|| (u.getEmail() != null && u.getEmail().toLowerCase().contains(needle)))
				// roleId filter: keep user if any of their roles match
				.filter(u -> roleId == null
						|| (u.getRoleIds() != null && u.getRoleIds().contains(roleId)))
				.map(u -> {
					String cName = companyNameMap.getOrDefault(u.getCompanyId(), null);
					String eName = employeeNameMap.getOrDefault(u.getEmployeeId(), null);
					return toDto(u, cName, eName, roleNameMap);
				})
				.toList();
	}

	// ── Update ────────────────────────────────────────────────────────────────

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

		// Replace roleIds with exactly what was sent (if any roles provided)
		List<Long> requestedRoleIds = resolveRoleIds(dto);
		if (!requestedRoleIds.isEmpty()) {
			validateRoles(requestedRoleIds, existing.getCompanyId());
			existing.setRoleIds(new ArrayList<>(requestedRoleIds));
		}

		// Email update
		if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(existing.getEmail())
				&& repo.existsByEmailIgnoreCase(dto.getEmail())) {
			throw new IllegalArgumentException("Email already in use");
		}
		if (dto.getEmail() != null) {
			existing.setEmail(dto.getEmail());
		}

		// Password — only update if non-blank
		if (dto.getPasswordHash() != null && !dto.getPasswordHash().isBlank()) {
			existing.setPasswordHash(dto.getPasswordHash());
		}

		if (dto.getIsActive() != null) {
			existing.setIsActive(dto.getIsActive());
		}

		UserAccount saved = repo.save(existing);

		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String employeeName = employeeRepo.findById(saved.getEmployeeId())
				.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
		Map<Long, String> roleMap = buildRoleMap(saved.getRoleIds());

		return toDto(saved, companyName, employeeName, roleMap);
	}

	// ── Delete ────────────────────────────────────────────────────────────────

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("User account not found");
		repo.deleteById(id);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * Map a UserAccount to a DTO with full multi-role fields populated.
	 * roleNameMap: roleId → roleName (pass in pre-loaded map to avoid N+1 queries)
	 */
	private UserAccountDto toDto(UserAccount u, String companyName, String employeeName,
			Map<Long, String> roleNameMap) {

		UserAccountDto dto = new UserAccountDto();
		dto.setUserId(u.getUserId());
		dto.setCompanyId(u.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setEmployeeId(u.getEmployeeId());
		dto.setEmployeeName(employeeName);
		dto.setEmail(u.getEmail());
		dto.setIsActive(u.getIsActive());

		List<Long> storedRoleIds = u.getRoleIds() != null ? u.getRoleIds() : List.of();
		dto.setRoleIds(storedRoleIds);

		// Build roleNames list and rich roles list
		List<String> names = new ArrayList<>();
		List<Map<String, Object>> richRoles = new ArrayList<>();
		for (Long rId : storedRoleIds) {
			String rName = roleNameMap.getOrDefault(rId, "Unknown Role");
			names.add(rName);
			Map<String, Object> roleObj = new LinkedHashMap<>();
			roleObj.put("roleId", rId);
			roleObj.put("roleName", rName);
			richRoles.add(roleObj);
		}
		dto.setRoleNames(names);
		dto.setRoles(richRoles);

		// Backward-compat single role fields — use first role in list
		if (!storedRoleIds.isEmpty()) {
			dto.setRoleId(storedRoleIds.get(0));
			dto.setRoleName(names.get(0));
		}

		return dto;
	}

	/** Build roleId → roleName map for a specific set of role IDs. */
	private Map<Long, String> buildRoleMap(List<Long> roleIds) {
		if (roleIds == null || roleIds.isEmpty()) return Map.of();
		return roleRepo.findAllById(roleIds).stream()
				.collect(Collectors.toMap(Role::getRoleId, Role::getRoleName));
	}

	/** Prefer dto.roleIds list; fall back to single dto.roleId. */
	private List<Long> resolveRoleIds(UserAccountDto dto) {
		if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
			return dto.getRoleIds();
		}
		if (dto.getRoleId() != null) {
			return List.of(dto.getRoleId());
		}
		return List.of();
	}

	/** Validate that all roles exist and belong to the given company. */
	private void validateRoles(List<Long> roleIds, Long companyId) {
		for (Long rId : roleIds) {
			Role role = roleRepo.findById(rId)
					.orElseThrow(() -> new IllegalArgumentException("Role not found: " + rId));
			if (!Objects.equals(role.getCompanyId(), companyId)) {
				throw new IllegalArgumentException(
						"Role " + rId + " does not belong to company " + companyId);
			}
		}
	}
}
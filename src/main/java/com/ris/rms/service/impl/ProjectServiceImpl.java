package com.ris.rms.service.impl;

import com.ris.rms.dto.ProjectDto;
import com.ris.rms.entity.*;
import com.ris.rms.repository.*;
import com.ris.rms.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {

	private final ProjectRepository repo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final SkillRepository skillRepo;
	private final ProjectSkillRepository projectSkillRepo;

	@Override
	public ProjectDto create(ProjectDto dto) {
		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (repo.existsByCompanyIdAndProjectNameIgnoreCase(dto.getCompanyId(), dto.getProjectName())) {
			throw new IllegalArgumentException("Project name already exists for this company");
		}

		String accountName = null;
		if (dto.getAccountId() != null) {
			Account acc = accountRepo.findById(dto.getAccountId())
					.orElseThrow(() -> new IllegalArgumentException("Account not found"));
			if (!acc.getCompanyId().equals(dto.getCompanyId())) {
				throw new IllegalArgumentException("Account must belong to the same company");
			}
			accountName = acc.getAccountName();
		}

		ManagerInfo mi = null;
		if (dto.getManagerUserId() != null) {
			UserAccount ua = userAccountRepo.findById(dto.getManagerUserId())
					.orElseThrow(() -> new IllegalArgumentException("Manager user not found"));
			if (!ua.getCompanyId().equals(dto.getCompanyId())) {
				throw new IllegalArgumentException("Manager must belong to the same company");
			}
			mi = resolveManagerInfo(dto.getManagerUserId());
		}

		validateDates(dto.getStartDate(), dto.getEndDate());

		Project entity = toEntity(dto);
		entity.setProjectId(null);
		Project saved = repo.save(entity);

		if (dto.getSkillIds() != null) {
			linkSkillsByIds(saved.getProjectId(), dto.getSkillIds());
		}

		ProjectDto out = withSkills(toDto(saved, comp.getCompanyName(), accountName, mi == null ? null : mi.name()),
				saved.getProjectId());
		if (mi != null) {
			out.setManagerEmployeeId(mi.employeeId());
			out.setManagerEmail(mi.email());
		}
		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public ProjectDto getById(Long id) {
		Project p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));

		String companyName = companyRepo.findById(p.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String accountName = p.getAccountId() == null ? null
				: accountRepo.findById(p.getAccountId()).map(Account::getAccountName).orElse(null);

		ManagerInfo mi = resolveManagerInfo(p.getManagerUserId());

		ProjectDto out = withSkills(toDto(p, companyName, accountName, mi == null ? null : mi.name()),
				p.getProjectId());
		if (mi != null) {
			out.setManagerEmployeeId(mi.employeeId());
			out.setManagerEmail(mi.email());
		}
		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProjectDto> list(Long companyId, Long accountId, Long managerUserId, String status, String priority,
			String q, Integer page, Integer size) {
		List<Project> base;
		if (companyId != null && accountId != null) {
			base = repo.findAllByCompanyIdAndAccountId(companyId, accountId);
		} else if (companyId != null) {
			base = repo.findAllByCompanyId(companyId);
		} else {
			base = (page != null && size != null && page >= 0 && size > 0)
					? repo.findAll(PageRequest.of(page, size)).getContent()
					: repo.findAll();
		}

		final String nl = q == null ? null : q.toLowerCase();
		final String sl = status == null ? null : status.toLowerCase();
		final String pl = priority == null ? null : priority.toLowerCase();

		return base.stream().filter(p -> managerUserId == null || Objects.equals(p.getManagerUserId(), managerUserId))
				.filter(p -> sl == null || (p.getStatus() != null && p.getStatus().toLowerCase().contains(sl)))
				.filter(p -> pl == null || (p.getPriority() != null && p.getPriority().toLowerCase().contains(pl)))
				.filter(p -> {
					if (nl == null || nl.isBlank())
						return true;
					return (p.getProjectName() != null && p.getProjectName().toLowerCase().contains(nl))
							|| (p.getDescription() != null && p.getDescription().toLowerCase().contains(nl));
				}).map(p -> {
					String companyName = companyRepo.findById(p.getCompanyId()).map(Company::getCompanyName)
							.orElse(null);
					String accountName2 = p.getAccountId() == null ? null
							: accountRepo.findById(p.getAccountId()).map(Account::getAccountName).orElse(null);
					ManagerInfo mi = resolveManagerInfo(p.getManagerUserId());

					ProjectDto out = withSkills(toDto(p, companyName, accountName2, mi == null ? null : mi.name()),
							p.getProjectId());
					if (mi != null) {
						out.setManagerEmployeeId(mi.employeeId());
						out.setManagerEmail(mi.email());
					}
					return out;
				}).toList();
	}

	@Override
	public ProjectDto update(Long id, ProjectDto dto) {
		Project existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Project not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}

		if (dto.getProjectName() != null && !dto.getProjectName().equalsIgnoreCase(existing.getProjectName())
				&& repo.existsByCompanyIdAndProjectNameIgnoreCase(existing.getCompanyId(), dto.getProjectName())) {
			throw new IllegalArgumentException("Project name already exists for this company");
		}

		String accountName = null;
		if (dto.getAccountId() != null) {
			Account acc = accountRepo.findById(dto.getAccountId())
					.orElseThrow(() -> new IllegalArgumentException("Account not found"));
			if (!acc.getCompanyId().equals(existing.getCompanyId())) {
				throw new IllegalArgumentException("Account must belong to the same company");
			}
			existing.setAccountId(dto.getAccountId());
			accountName = acc.getAccountName();
		}
		ManagerInfo mi = null;
		if (dto.getManagerUserId() != null) {
			UserAccount ua = userAccountRepo.findById(dto.getManagerUserId())
					.orElseThrow(() -> new IllegalArgumentException("Manager user not found"));
			if (!ua.getCompanyId().equals(existing.getCompanyId())) {
				throw new IllegalArgumentException("Manager must belong to the same company");
			}
			existing.setManagerUserId(dto.getManagerUserId());
			mi = resolveManagerInfo(dto.getManagerUserId());
		} else {

			if (dto.getManagerName() == null) {
				mi = resolveManagerInfo(existing.getManagerUserId());
			}
		}

		LocalDate start = dto.getStartDate() != null ? dto.getStartDate() : existing.getStartDate();
		LocalDate end = dto.getEndDate() != null ? dto.getEndDate() : existing.getEndDate();
		validateDates(start, end);

		if (dto.getProjectName() != null)
			existing.setProjectName(dto.getProjectName());
		if (dto.getDescription() != null)
			existing.setDescription(dto.getDescription());
		if (dto.getStartDate() != null)
			existing.setStartDate(dto.getStartDate());
		if (dto.getEndDate() != null)
			existing.setEndDate(dto.getEndDate());
		if (dto.getBudget() != null)
			existing.setBudget(dto.getBudget());
		if (dto.getRevenueAmount() != null)
			existing.setRevenueAmount(dto.getRevenueAmount());
		if (dto.getPriority() != null)
			existing.setPriority(dto.getPriority());
		if (dto.getStatus() != null)
			existing.setStatus(dto.getStatus());

		Project saved = repo.save(existing);

		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		if (accountName == null && saved.getAccountId() != null) {
			accountName = accountRepo.findById(saved.getAccountId()).map(Account::getAccountName).orElse(null);
		}
		if (mi == null && saved.getManagerUserId() != null) {
			mi = resolveManagerInfo(saved.getManagerUserId());
		}

		if (dto.getSkillIds() != null) {
			linkSkillsByIds(saved.getProjectId(), dto.getSkillIds());
		}

		ProjectDto out = withSkills(toDto(saved, companyName, accountName, mi == null ? null : mi.name()),
				saved.getProjectId());
		if (mi != null) {
			out.setManagerEmployeeId(mi.employeeId());
			out.setManagerEmail(mi.email());
		}
		return out;
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("Project not found");
		repo.deleteById(id);
	}

	private record ManagerInfo(String name, Long employeeId, String email) {
	}

	private ManagerInfo resolveManagerInfo(Long managerUserId) {
		if (managerUserId == null)
			return null;
		return userAccountRepo.findById(managerUserId).map(ua -> {
			String name = employeeRepo.findById(ua.getEmployeeId()).map(e -> e.getFirstName() + " " + e.getLastName())
					.orElse(null);
			return new ManagerInfo(name, ua.getEmployeeId(), ua.getEmail());
		}).orElse(null);
	}

	private void validateDates(LocalDate start, LocalDate end) {
		if (start != null && end != null && end.isBefore(start)) {
			throw new IllegalArgumentException("endDate must be on/after startDate");
		}
	}

	private ProjectDto toDto(Project p, String companyName, String accountName, String managerName) {
		ProjectDto dto = new ProjectDto();
		dto.setProjectId(p.getProjectId());
		dto.setCompanyId(p.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setAccountId(p.getAccountId());
		dto.setAccountName(accountName);
		dto.setManagerUserId(p.getManagerUserId());
		dto.setManagerName(managerName);
		dto.setProjectName(p.getProjectName());
		dto.setDescription(p.getDescription());
		dto.setStartDate(p.getStartDate());
		dto.setEndDate(p.getEndDate());
		dto.setBudget(p.getBudget());
		dto.setRevenueAmount(p.getRevenueAmount());
		dto.setPriority(p.getPriority());
		dto.setStatus(p.getStatus());
		return dto;
	}

	private Project toEntity(ProjectDto dto) {
		Project p = new Project();
		p.setProjectId(dto.getProjectId());
		p.setCompanyId(dto.getCompanyId());
		p.setAccountId(dto.getAccountId());
		p.setManagerUserId(dto.getManagerUserId());
		p.setProjectName(dto.getProjectName());
		p.setDescription(dto.getDescription());
		p.setStartDate(dto.getStartDate());
		p.setEndDate(dto.getEndDate());
		p.setBudget(dto.getBudget());
		p.setRevenueAmount(dto.getRevenueAmount());
		p.setPriority(dto.getPriority());
		p.setStatus(dto.getStatus());
		return p;
	}

	private void linkSkillsByIds(Long projectId, java.util.List<Long> skillIds) {
		projectSkillRepo.deleteAllByProjectId(projectId);
		if (skillIds == null)
			return;
		for (Long sid : skillIds) {
			if (sid == null)
				continue;
			if (!skillRepo.existsById(sid)) {
				throw new IllegalArgumentException("Skill not found: " + sid);
			}
			ProjectSkill ps = new ProjectSkill();
			ps.setProjectId(projectId);
			ps.setSkillId(sid);
			projectSkillRepo.save(ps);
		}
	}

	private java.util.List<Long> loadSkillIds(Long projectId) {
		return projectSkillRepo.findAllByProjectId(projectId).stream().map(ProjectSkill::getSkillId).toList();
	}

	private java.util.List<String> loadSkillNames(java.util.List<Long> skillIds) {
		return skillIds.stream().map(id -> skillRepo.findById(id).map(Skill::getSkillName).orElse(null))
				.filter(java.util.Objects::nonNull).toList();
	}

	private ProjectDto withSkills(ProjectDto dto, Long projectId) {
		var ids = loadSkillIds(projectId);
		var names = loadSkillNames(ids);
		dto.setSkillIds(ids);
		dto.setSkills(names);
		return dto;
	}
}

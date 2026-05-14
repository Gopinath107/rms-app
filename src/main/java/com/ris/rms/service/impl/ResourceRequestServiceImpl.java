package com.ris.rms.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ris.rms.dto.ResourceRequestDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.ResourceRequestSkill;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.CandidateRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.ResourceRequestSkillRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.ResourceRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ResourceRequestServiceImpl implements ResourceRequestService {

	private final ResourceRequestRepository repo;
	private final ResourceRequestSkillRepository rrSkillRepo;

	private final ProjectRepository projectRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final SkillRepository skillRepo;
	private final RoleRepository roleRepo;
	private final EmailService emailService;

	private final DemandRepository demandRepo;
	private final ResReqGroupRepository groupRepo;
	private final CandidateRepository candidateRepo;
	
	private static final Set<String> HR_ALIASES = Set.of("hr", "humanresources", "humanresource");

	private static String normRole(String s) {
		return s == null ? null : s.replaceAll("[^A-Za-z]", "").toLowerCase();
	}

	private List<UserAccount> hrUsers(Long companyId) {
		var roles = roleRepo.findAllByCompanyId(companyId).stream()
				.filter(r -> HR_ALIASES.contains(normRole(r.getRoleName()))).toList();
		if (!roles.isEmpty()) {
			var rids = roles.stream().map(Role::getRoleId).toList();
			return userAccountRepo.findAll().stream().filter(u -> Objects.equals(u.getCompanyId(), companyId))
					.filter(u -> u.getRoleId() != null && rids.contains(u.getRoleId())).toList();
		}

		return userAccountRepo.findAll().stream().filter(u -> Objects.equals(u.getCompanyId(), companyId))
				.filter(u -> u.getRoleId() != null).filter(u -> roleRepo.findById(u.getRoleId()).map(Role::getRoleName)
						.map(ResourceRequestServiceImpl::normRole).map(HR_ALIASES::contains).orElse(false))
				.toList();
	}

	@Override
	public ResourceRequestDto create(ResourceRequestDto dto) {
		Project project = null;
		Long companyId = null;

		if (dto.getProjectId() != null) {
			project = projectRepo.findById(dto.getProjectId()).orElseThrow(
					() -> new IllegalArgumentException("Project not found with ID: " + dto.getProjectId()));
			companyId = project.getCompanyId();
		}

		String requesterName = null, requesterEmail = null;

		if (dto.getRequesterUserId() != null) {
			UserAccount ua = userAccountRepo.findById(dto.getRequesterUserId())
					.orElseThrow(() -> new IllegalArgumentException("Requester user not found"));

			if (project != null && !ua.getCompanyId().equals(project.getCompanyId())) {
				throw new IllegalArgumentException("Requester must belong to the same company as the project");
			}

			if (companyId == null) {
				companyId = ua.getCompanyId();
			}

			requesterEmail = ua.getEmail();
			requesterName = employeeRepo.findById(ua.getEmployeeId()).map(e -> e.getFirstName() + " " + e.getLastName())
					.orElse(null);
		}

		if (companyId == null) {
			throw new IllegalArgumentException(
					"Cannot determine company. Please provide a valid projectId or requesterUserId.");
		}

		ResourceRequest rr = toEntity(dto);
		rr.setRequestId(null);
		rr.setStatus("Submitted");
		rr.setSubmittedDate(LocalDate.now());
		ResourceRequest saved = repo.save(rr);

		boolean hasPrimary = dto.getPrimarySkillIds() != null && !dto.getPrimarySkillIds().isEmpty();
		boolean hasSecondary = dto.getSecondarySkillIds() != null && !dto.getSecondarySkillIds().isEmpty();
		if (hasPrimary || hasSecondary) {
			linkSkillsByType(saved.getRequestId(), dto.getPrimarySkillIds(), "Primary");
			linkSkillsByType(saved.getRequestId(), dto.getSecondarySkillIds(), "Secondary");
		} else if (dto.getSkillIds() != null && !dto.getSkillIds().isEmpty()) {
			linkSkillsByType(saved.getRequestId(), dto.getSkillIds(), "Primary");
		}

		String companyName = companyRepo.findById(companyId).map(Company::getCompanyName).orElse(null);
		String accountName = null;
		if (project != null && project.getAccountId() != null) {
			accountName = accountRepo.findById(project.getAccountId()).map(Account::getAccountName).orElse(null);
		}

		ResourceRequestDto out = fillSkillNames(
				toDto(saved, project, companyName, accountName, requesterName, requesterEmail));

		if (dto.getDemandId() != null && accountName == null) {
			demandRepo.findById(dto.getDemandId()).ifPresent(d -> {
				accountRepo.findById(d.getAccountId()).ifPresent(a -> out.setAccountName(a.getAccountName()));
			});
		}

		try {
			String projName = out.getProjectName();
			String accName = out.getAccountName();
			String submitted = DateTimeFormatter.ofPattern("dd MMM uuuu").format(out.getSubmittedDate());

			String primaryCsv = (out.getPrimarySkills() == null || out.getPrimarySkills().isEmpty()) ? ""
					: String.join(", ", out.getPrimarySkills());
			String secondaryCsv = (out.getSecondarySkills() == null || out.getSecondarySkills().isEmpty()) ? ""
					: String.join(", ", out.getSecondarySkills());

			List<UserAccount> hrList = hrUsers(companyId);
			List<CompletableFuture<Boolean>> futures = new ArrayList<>();
			for (UserAccount hr : hrList) {
				if (hr.getEmail() == null || hr.getEmail().isBlank())
					continue;
				String hrName = employeeRepo.findById(hr.getEmployeeId())
						.map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse("HR");

				futures.add(emailService.sendHrResReqCreatedAsync(hr.getEmail(), hrName, projName, accName,
						out.getRequestId(), submitted, out.getPriority(), out.getNumberOfResources(),
						out.getExperienceRange(), out.getLocation(), out.getWorkMode(), out.getLocationType(),
						primaryCsv, secondaryCsv));
			}
//			if (!futures.isEmpty()) {
//				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//			}
		} catch (Exception ex) {
			System.err.println("HR email (single request) failed: " + ex.getMessage());
		}

		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public ResourceRequestDto getById(Long id) {
		ResourceRequest rr = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Resource request not found"));

		String requesterName = null, requesterEmail = null;
		if (rr.getRequesterUserId() != null) {
			UserAccount ua = userAccountRepo.findById(rr.getRequesterUserId()).orElse(null);
			if (ua != null) {
				requesterEmail = ua.getEmail();
				requesterName = employeeRepo.findById(ua.getEmployeeId())
						.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
			}
		}

		ResourceRequestDto dto;

		if (rr.getDemandId() != null) {
			Demand d = demandRepo.findById(rr.getDemandId()).orElse(null);
			String companyName = null;
			String accountName = null;

			if (d != null) {
				companyName = companyRepo.findById(d.getCompanyId()).map(Company::getCompanyName).orElse(null);
				accountName = accountRepo.findById(d.getAccountId()).map(Account::getAccountName).orElse(null);
				dto = toDto(rr, null, companyName, accountName, requesterName, requesterEmail);

				dto.setProjectName(d.getProjectName());
				dto.setCompanyId(d.getCompanyId());
				dto.setAccountId(d.getAccountId());
			} else {
				dto = toDto(rr, null, null, null, requesterName, requesterEmail);
			}
			dto.setDemandId(rr.getDemandId());

		} else if (rr.getProjectId() != null) {

			Project project = projectRepo.findById(rr.getProjectId()).orElse(null);
			String companyName = project == null ? null
					: companyRepo.findById(project.getCompanyId()).map(Company::getCompanyName).orElse(null);
			String accountName = (project == null || project.getAccountId() == null) ? null
					: accountRepo.findById(project.getAccountId()).map(Account::getAccountName).orElse(null);
			dto = toDto(rr, project, companyName, accountName, requesterName, requesterEmail);

		} else {
			dto = toDto(rr, null, null, null, requesterName, requesterEmail);
			if (rr.getRequesterUserId() != null) {
				userAccountRepo.findById(rr.getRequesterUserId()).ifPresent(ua -> {
					dto.setCompanyId(ua.getCompanyId());
					companyRepo.findById(ua.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
				});
			}
		}

		return fillSkillNames(dto);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResourceRequestDto> list(Long companyId, Long projectId, Long groupId, String status, String priority,
			String q, Integer page, Integer size) {

		List<ResourceRequest> base = (page != null && size != null && page >= 0 && size > 0)
				? repo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedDate", "requestId")))
						.getContent()
				: repo.findAll().stream().sorted(Comparator
						.comparing(ResourceRequest::getSubmittedDate, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ResourceRequest::getRequestId, Comparator.nullsLast(Comparator.reverseOrder())))
						.toList();

		String sl = status == null ? null : status.toLowerCase();
		String pl = priority == null ? null : priority.toLowerCase();
		String nl = q == null ? null : q.toLowerCase();

		return base.stream().filter(rr -> projectId == null || Objects.equals(rr.getProjectId(), projectId))
				.filter(rr -> groupId == null || Objects.equals(rr.getGroupId(), groupId)).filter(rr -> {
					if (companyId == null)
						return true;

					if (rr.getProjectId() != null) {
						return projectRepo.findById(rr.getProjectId())
								.map(p -> Objects.equals(p.getCompanyId(), companyId)).orElse(false);
					}
					if (rr.getDemandId() != null) {
						return demandRepo.findById(rr.getDemandId())
								.map(d -> Objects.equals(d.getCompanyId(), companyId)).orElse(false);
					}
					if (rr.getRequesterUserId() != null) {
						return userAccountRepo.findById(rr.getRequesterUserId())
								.map(u -> Objects.equals(u.getCompanyId(), companyId)).orElse(false);
					}
					return false;
				}).filter(rr -> sl == null || (rr.getStatus() != null && rr.getStatus().toLowerCase().contains(sl)))
				.filter(rr -> pl == null || (rr.getPriority() != null && rr.getPriority().toLowerCase().contains(pl)))
				.filter(rr -> {
					if (nl == null || nl.isBlank())
						return true;
					return (rr.getExperienceRange() != null && rr.getExperienceRange().toLowerCase().contains(nl))
							|| (rr.getLocation() != null && rr.getLocation().toLowerCase().contains(nl))
							|| (rr.getWorkMode() != null && rr.getWorkMode().toLowerCase().contains(nl));
				}).map(rr -> {
					String requesterName = null, requesterEmail = null;
					if (rr.getRequesterUserId() != null) {
						UserAccount ua = userAccountRepo.findById(rr.getRequesterUserId()).orElse(null);
						if (ua != null) {
							requesterEmail = ua.getEmail();
							requesterName = employeeRepo.findById(ua.getEmployeeId())
									.map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
						}
					}

					ResourceRequestDto dto;

					if (rr.getDemandId() != null) {

						Demand d = demandRepo.findById(rr.getDemandId()).orElse(null);
						String companyName = null;
						String accountName = null;
						if (d != null) {
							companyName = companyRepo.findById(d.getCompanyId()).map(Company::getCompanyName)
									.orElse(null);
							accountName = accountRepo.findById(d.getAccountId()).map(Account::getAccountName)
									.orElse(null);
							dto = toDto(rr, null, companyName, accountName, requesterName, requesterEmail);
							dto.setProjectName(d.getProjectName());
							dto.setCompanyId(d.getCompanyId());
							dto.setAccountId(d.getAccountId());
						} else {
							dto = toDto(rr, null, null, null, requesterName, requesterEmail);
						}
						dto.setDemandId(rr.getDemandId());

					} else if (rr.getProjectId() != null) {
						Project p = projectRepo.findById(rr.getProjectId()).orElse(null);
						String companyName = p == null ? null
								: companyRepo.findById(p.getCompanyId()).map(Company::getCompanyName).orElse(null);
						String accountName = (p == null || p.getAccountId() == null) ? null
								: accountRepo.findById(p.getAccountId()).map(Account::getAccountName).orElse(null);
						dto = toDto(rr, p, companyName, accountName, requesterName, requesterEmail);

					} else {
						dto = toDto(rr, null, null, null, requesterName, requesterEmail);
						if (rr.getRequesterUserId() != null) {
							userAccountRepo.findById(rr.getRequesterUserId()).ifPresent(ua -> {
								dto.setCompanyId(ua.getCompanyId());
								companyRepo.findById(ua.getCompanyId())
										.ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
							});
						}
					}

					return fillSkillNames(dto);
				}).collect(Collectors.toList());
	}

	@Override
	public ResourceRequestDto update(Long id, ResourceRequestDto dto) {
		ResourceRequest existing = repo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Resource request not found"));

		if (dto.getProjectId() != null && existing.getDemandId() != null) {
			throw new IllegalArgumentException("A Demand-based request cannot be assigned a projectId");
		}
		if (dto.getProjectId() != null && !dto.getProjectId().equals(existing.getProjectId())) {
			throw new IllegalArgumentException("projectId cannot be changed");
		}
		if (dto.getDemandId() != null && !dto.getDemandId().equals(existing.getDemandId())) {
			throw new IllegalArgumentException("demandId cannot be changed");
		}

		if (dto.getRequesterUserId() != null) {

			Long companyId = null;
			if (existing.getProjectId() != null) {
				companyId = projectRepo.findById(existing.getProjectId()).map(Project::getCompanyId).orElse(null);
			} else if (existing.getDemandId() != null) {
				companyId = demandRepo.findById(existing.getDemandId()).map(Demand::getCompanyId).orElse(null);
			} else if (existing.getRequesterUserId() != null) {
				companyId = userAccountRepo.findById(existing.getRequesterUserId()).map(UserAccount::getCompanyId)
						.orElse(null);
			}

			UserAccount ua = userAccountRepo.findById(dto.getRequesterUserId())
					.orElseThrow(() -> new IllegalArgumentException("Requester user not found"));

			if (companyId != null && !ua.getCompanyId().equals(companyId)) {
				throw new IllegalArgumentException("Requester must belong to the same company as the request");
			}
			existing.setRequesterUserId(dto.getRequesterUserId());
		}

		if (dto.getNumberOfResources() != null)
			existing.setNumberOfResources(dto.getNumberOfResources());
		if (dto.getExperienceRange() != null)
			existing.setExperienceRange(dto.getExperienceRange());
		if (dto.getLocationType() != null)
			existing.setLocationType(dto.getLocationType());
		if (dto.getWorkMode() != null)
			existing.setWorkMode(dto.getWorkMode());
		if (dto.getLocation() != null)
			existing.setLocation(dto.getLocation());
		if (dto.getPriority() != null)
			existing.setPriority(dto.getPriority());

		if (dto.getEstimatedCostTotal() != null)
			existing.setEstimatedCostTotal(safeNonNeg(dto.getEstimatedCostTotal()));
		if (dto.getEstimatedCostPerResourceMonth() != null)
			existing.setEstimatedCostPerResourceMonth(safeNonNeg(dto.getEstimatedCostPerResourceMonth()));

		if (dto.getStatus() != null || dto.getSubmittedDate() != null) {
			throw new IllegalArgumentException("status/submittedDate cannot be changed here; use HR decision API");
		}

		ResourceRequest saved = repo.save(existing);

		if ((dto.getPrimarySkillIds() != null) || (dto.getSecondarySkillIds() != null)) {
			rrSkillRepo.deleteAllByRequestId(saved.getRequestId());
			linkSkillsByType(saved.getRequestId(), dto.getPrimarySkillIds(), "Primary");
			linkSkillsByType(saved.getRequestId(), dto.getSecondarySkillIds(), "Secondary");
		} else if (dto.getSkillIds() != null) {
			rrSkillRepo.deleteAllByRequestId(saved.getRequestId());
			linkSkillsByType(saved.getRequestId(), dto.getSkillIds(), "Primary");
		}

		return getById(saved.getRequestId());
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("Resource request not found");
		repo.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResourceRequestDto> getByDemand(Long demandId) {
		List<ResourceRequest> rrs = repo.findActualByDemandId(demandId);

		Demand demand = demandRepo.findById(demandId).orElse(null);
		String demandTitle = demand != null ? demand.getDemandtitle() : null;
		String accountName = (demand != null && demand.getAccountId() != null)
				? accountRepo.findById(demand.getAccountId()).map(Account::getAccountName).orElse(null)
				: null;
		String companyName = (demand != null)
				? companyRepo.findById(demand.getCompanyId()).map(Company::getCompanyName).orElse(null)
				: null;

		return rrs.stream().map(rr -> {
			ResourceRequestDto dto = toDto(rr, null, companyName, accountName, null, null);
			dto.setDemandId(demandId);
			dto.setDemandTitle(demandTitle);
			dto.setResourceType(rr.getResourceType());
			dto.setEmployeeId(rr.getEmployeeId());
			dto.setCandidateId(rr.getCandidateId());

			// Resolve candidate/employee display name
			if ("INTERNAL".equals(rr.getResourceType()) && rr.getEmployeeId() != null) {
				employeeRepo.findById(rr.getEmployeeId()).ifPresent(e ->
					dto.setCandidateName(((e.getFirstName() == null ? "" : e.getFirstName()) + " "
							+ (e.getLastName() == null ? "" : e.getLastName())).trim())
				);
			} else if ("EXTERNAL".equals(rr.getResourceType()) && rr.getCandidateId() != null) {
				candidateRepo.findById(rr.getCandidateId()).ifPresent(c ->
					dto.setCandidateName(((c.getFirstName() == null ? "" : c.getFirstName()) + " "
							+ (c.getLastName() == null ? "" : c.getLastName())).trim())
				);
			}
			return fillSkillNames(dto);
		}).collect(Collectors.toList());
	}

	private BigDecimal safeNonNeg(BigDecimal val) {
		if (val == null)
			return null;
		if (val.signum() < 0)
			throw new IllegalArgumentException("Amount must be >= 0");
		return val;
	}

	private void linkSkillsByType(Long requestId, List<Long> skillIds, String type) {
		if (skillIds == null || skillIds.isEmpty())
			return;
		for (Long sid : skillIds) {
			if (sid == null)
				continue;
			if (!skillRepo.existsById(sid))
				throw new IllegalArgumentException("Skill not found: " + sid);
			ResourceRequestSkill rs = new ResourceRequestSkill();
			rs.setRequestId(requestId);
			rs.setSkillId(sid);
			rs.setSkillType(type);
			rrSkillRepo.save(rs);
		}
	}

	private ResourceRequestDto fillSkillNames(ResourceRequestDto dto) {

		List<ResourceRequestSkill> rows = rrSkillRepo.findAllByRequestId(dto.getRequestId());

		List<Long> primaryIds = rows.stream().filter(r -> "Primary".equalsIgnoreCase(r.getSkillType()))
				.map(ResourceRequestSkill::getSkillId).toList();
		List<Long> secondaryIds = rows.stream().filter(r -> "Secondary".equalsIgnoreCase(r.getSkillType()))
				.map(ResourceRequestSkill::getSkillId).toList();

		dto.setPrimarySkillIds(primaryIds);
		dto.setSecondarySkillIds(secondaryIds);

		dto.setPrimarySkills(primaryIds.stream().map(id -> skillRepo.findById(id).map(Skill::getSkillName).orElse(null))
				.filter(Objects::nonNull).toList());
		dto.setSecondarySkills(
				secondaryIds.stream().map(id -> skillRepo.findById(id).map(Skill::getSkillName).orElse(null))
						.filter(Objects::nonNull).toList());

		List<Long> allIds = new ArrayList<>();
		allIds.addAll(primaryIds);
		allIds.addAll(secondaryIds);
		dto.setSkillIds(allIds);
		dto.setSkills(allIds.stream().map(id -> skillRepo.findById(id).map(Skill::getSkillName).orElse(null))
				.filter(Objects::nonNull).toList());
		return dto;
	}

	private ResourceRequestDto toDto(ResourceRequest rr, Project p, String companyName, String accountName,
			String requesterName, String requesterEmail) {
		ResourceRequestDto dto = new ResourceRequestDto();
		dto.setRequestId(rr.getRequestId());
		dto.setProjectId(rr.getProjectId());
		dto.setAccountName(accountName);
        dto.setCompanyName(companyName);
		if (p != null) {
			dto.setCompanyId(p.getCompanyId());
			
			dto.setProjectName(p.getProjectName());
			dto.setAccountId(p.getAccountId());
			
		}
		dto.setRequesterUserId(rr.getRequesterUserId());
		dto.setRequesterName(requesterName);
		dto.setRequesterEmail(requesterEmail);

		dto.setNumberOfResources(rr.getNumberOfResources());
		dto.setExperienceRange(rr.getExperienceRange());
		dto.setLocationType(rr.getLocationType());
		dto.setWorkMode(rr.getWorkMode());
		dto.setLocation(rr.getLocation());
		dto.setPriority(rr.getPriority());
		dto.setStatus(rr.getStatus());
		dto.setSubmittedDate(rr.getSubmittedDate());

		if (dto.getSubmittedDate() != null && "Submitted".equalsIgnoreCase(dto.getStatus())) {
			dto.setDaysPending(
					java.time.temporal.ChronoUnit.DAYS.between(dto.getSubmittedDate(), java.time.LocalDate.now()));
		}
		dto.setEstimatedCostTotal(rr.getEstimatedCostTotal());
		dto.setEstimatedCostPerResourceMonth(rr.getEstimatedCostPerResourceMonth());

		dto.setGroupId(rr.getGroupId());
		dto.setDemandId(rr.getDemandId());
		if (rr.getDemandId() != null) {
	        demandRepo.findById(rr.getDemandId()).ifPresent(d -> {
	            dto.setDemandTitle(d.getDemandtitle());
	            dto.setDemandDescription(d.getDescription());
	        });
	    }
		if (rr.getGroupId() != null) {
	        groupRepo.findById(rr.getGroupId()).ifPresent(g -> {
	            dto.setGroupTitle(g.getTitle());
	        });
	    }

		// Map new resource/candidate linking fields
		dto.setEmployeeId(rr.getEmployeeId());
		dto.setCandidateId(rr.getCandidateId());
		dto.setResourceType(rr.getResourceType());

		return dto;
	}

	private ResourceRequest toEntity(ResourceRequestDto dto) {
		ResourceRequest rr = new ResourceRequest();
		rr.setRequestId(dto.getRequestId());
		rr.setProjectId(dto.getProjectId());
		rr.setRequesterUserId(dto.getRequesterUserId());
		rr.setNumberOfResources(dto.getNumberOfResources());
		rr.setExperienceRange(dto.getExperienceRange());
		rr.setLocationType(dto.getLocationType());
		rr.setWorkMode(dto.getWorkMode());
		rr.setLocation(dto.getLocation());
		rr.setPriority(dto.getPriority());
		rr.setEstimatedCostTotal(dto.getEstimatedCostTotal());
		rr.setEstimatedCostPerResourceMonth(dto.getEstimatedCostPerResourceMonth());

		rr.setGroupId(dto.getGroupId());
		rr.setDemandId(dto.getDemandId());
		return rr;
	}
}
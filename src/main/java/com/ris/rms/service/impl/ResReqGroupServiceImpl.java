package com.ris.rms.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ris.rms.dto.BulkCreateResReqDto;
import com.ris.rms.dto.BulkCreateResponseDto;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.dto.ProjectDto;
import com.ris.rms.dto.ResourceRequestGroupDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Allocation;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;
import com.ris.rms.entity.Interview;
import com.ris.rms.entity.Notification;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.ResourceRequestGroup;
import com.ris.rms.entity.ResourceRequestSkill;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CandidateDocumentRepository;
import com.ris.rms.repository.CandidateRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.repository.EmployeeDocumentRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.InterviewRepository;
import com.ris.rms.repository.NotificationRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.ResourceRequestSkillRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.ProjectService;
import com.ris.rms.service.ResReqGroupService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResReqGroupServiceImpl implements ResReqGroupService {

	private final ResReqGroupRepository groupRepo;
	private final ResourceRequestRepository rrRepo;
	private final ResourceRequestSkillRepository rrSkillRepo;
	private final SkillRepository skillRepo;

	private final ProjectRepository projectRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final DepartmentRepository departmentRepo;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final RoleRepository roleRepo;
	private final NotificationRepository notificationRepo;
	private final ProjectService projectService;
	private final EmailService emailService;
	private final InterviewRepository interviewRepo;
	private final AllocationRepository allocationRepo;
	private final EmployeeDocumentRepository employeeDocumentRepo;
	private final CandidateRepository candidateRepo;
	private final CandidateDocumentRepository candidateDocumentRepo;

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
						.map(ResReqGroupServiceImpl::normRole).map(HR_ALIASES::contains).orElse(false))
				.toList();
	}

	private static String joinSkillNames(SkillRepository repo, List<Long> ids) {
		if (ids == null || ids.isEmpty())
			return "";
		return ids.stream().map(id -> repo.findById(id).map(Skill::getSkillName).orElse(null)).filter(Objects::nonNull)
				.distinct().reduce((a, b) -> a + ", " + b).orElse("");
	}

	private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Override
	public BulkCreateResponseDto bulkCreate(BulkCreateResReqDto dto) {

		validateBulk(dto);
		validateSkillIds(dto.getPrimarySkillIds());
		validateSkillIds(dto.getSecondarySkillIds());
		validateSkillIds(dto.getSkillIds());

		Project project = projectRepo.findById(dto.getProjectId())
				.orElseThrow(() -> new IllegalArgumentException("Project not found"));
		Long companyId = project.getCompanyId();

		ProjectDto projectDto = projectService.getById(dto.getProjectId());

		userAccountRepo.findById(dto.getRequesterUserId()).filter(u -> Objects.equals(u.getCompanyId(), companyId))
				.orElseThrow(
						() -> new IllegalArgumentException("Requester must belong to the same company as the project"));

		ResourceRequestGroup group = new ResourceRequestGroup();
		group.setCompanyId(companyId);
		group.setProjectId(dto.getProjectId());
		group.setCreatedBy(dto.getRequesterUserId());
		group.setTitle(dto.getGroupTitle());
		group.setTotalRequested(dto.getCount());
		group.setStatus("Draft");

		group.setRequestTemplate(buildTemplateMap(dto));
		ResourceRequestGroup savedGroup = groupRepo.save(group);

		ResourceRequestGroupDto groupDto = toGroupDto(savedGroup);

		groupDto.setCompanyName(companyRepo.findById(companyId).map(Company::getCompanyName).orElse(null));
		groupDto.setProjectDetails(projectDto);

		if (savedGroup.getCreatedBy() != null) {
			userAccountRepo.findById(savedGroup.getCreatedBy()).ifPresent(ua -> {
				groupDto.setCreatedByEmail(ua.getEmail());
				Long empId = ua.getEmployeeId();
				if (empId != null) {
					employeeRepo.findById(empId).ifPresent(
							e -> groupDto.setCreatedByName((e.getFirstName() + " " + e.getLastName()).trim()));
				}
			});
		}

		try {
			String primaryCsv = joinSkillNames(skillRepo, dto.getPrimarySkillIds());
			String secondaryCsv = joinSkillNames(skillRepo, dto.getSecondarySkillIds());
			String submitted = DateTimeFormatter.ofPattern("dd MMM uuuu").format(LocalDate.now());
			String accountName = projectDto.getAccountName();

			List<UserAccount> hrList = hrUsers(companyId);
			List<CompletableFuture<Boolean>> futures = new ArrayList<>();
			for (UserAccount hr : hrList) {
				if (hr.getEmail() == null || hr.getEmail().isBlank())
					continue;
				String hrName = employeeRepo.findById(hr.getEmployeeId())
						.map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse("HR");

				futures.add(emailService.sendHrOpportunityCreatedAsync(hr.getEmail(), hrName,
						projectDto.getProjectName(), accountName, dto.getGroupTitle(), dto.getCount(), submitted,
						dto.getPriority(), dto.getExperienceRange(), dto.getLocation(), dto.getWorkMode(),
						dto.getLocationType(), primaryCsv, secondaryCsv));
			}
			if (!futures.isEmpty()) {
				CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
			}
		} catch (Exception ex) {
			System.err.println("HR email (opportunity) failed: " + ex.getMessage());
		}

		BulkCreateResponseDto out = new BulkCreateResponseDto();
		out.setGroup(groupDto);
		out.setCreatedRequests(List.of());
		return out;
	}

	private Map<String, Object> buildTemplateMap(BulkCreateResReqDto dto) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("experienceRange", dto.getExperienceRange());
		map.put("locationType", dto.getLocationType());
		map.put("workMode", dto.getWorkMode());
		map.put("location", dto.getLocation());
		map.put("priority", dto.getPriority());
		map.put("estimatedCostTotal", dto.getEstimatedCostTotal());
		map.put("estimatedCostPerResourceMonth", dto.getEstimatedCostPerResourceMonth());
		map.put("primarySkillIds", dto.getPrimarySkillIds());
		map.put("secondarySkillIds", dto.getSecondarySkillIds());
		map.put("skillIds", dto.getSkillIds());
		return map;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResourceRequestGroupDto> list(Long companyId, Long projectId, Integer page, Integer size) {
		List<ResourceRequestGroup> base = (page != null && size != null && page >= 0 && size > 0)
				? groupRepo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "groupId")))
						.getContent()
				: groupRepo.findAll().stream()
						.sorted(Comparator
								.comparing(ResourceRequestGroup::getCreatedAt,
										Comparator.nullsLast(Comparator.reverseOrder()))
								.thenComparing(ResourceRequestGroup::getGroupId,
										Comparator.nullsLast(Comparator.reverseOrder())))
						.toList();

		return base.stream().filter(g -> companyId == null || Objects.equals(g.getCompanyId(), companyId))
				.filter(g -> projectId == null || Objects.equals(g.getProjectId(), projectId)).map(g -> {
					ResourceRequestGroupDto dto = toGroupDto(g);
					dto.setCompanyName(
							companyRepo.findById(g.getCompanyId()).map(Company::getCompanyName).orElse(null));

					try {
						ProjectDto projectDto = projectService.getById(g.getProjectId());
						dto.setProjectDetails(projectDto);
					} catch (Exception e) {
						System.err.println("WARN: Project not found for group " + g.getGroupId());
					}

					if (g.getCreatedBy() != null) {
						userAccountRepo.findById(g.getCreatedBy()).ifPresent(ua -> {
							dto.setCreatedByEmail(ua.getEmail());
							if (ua.getEmployeeId() != null) {
								employeeRepo.findById(ua.getEmployeeId())
										.ifPresent(e -> dto.setCreatedByName(e.getFirstName() + " " + e.getLastName()));
							}
						});
					}
					return dto;
				}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ResourceRequestGroupDto getById(Long groupId) {
		ResourceRequestGroup g = groupRepo.findById(groupId)
				.orElseThrow(() -> new IllegalArgumentException("Group not found"));

		ResourceRequestGroupDto dto = toGroupDto(g);
		dto.setCompanyName(companyRepo.findById(g.getCompanyId()).map(Company::getCompanyName).orElse(null));

		ProjectDto projectDto = projectService.getById(g.getProjectId());
		dto.setProjectDetails(projectDto);

		if (g.getCreatedBy() != null) {
			userAccountRepo.findById(g.getCreatedBy()).ifPresent(ua -> {
				dto.setCreatedByEmail(ua.getEmail());
				if (ua.getEmployeeId() != null) {
					employeeRepo.findById(ua.getEmployeeId())
							.ifPresent(e -> dto.setCreatedByName(e.getFirstName() + " " + e.getLastName()));
				}
			});
		}
		return dto;
	}

	@Override
	public void recomputeGroupStatus(Long groupId) {
		ResourceRequestGroup g = groupRepo.findById(groupId)
				.orElseThrow(() -> new IllegalArgumentException("Group not found"));
		List<ResourceRequest> children = rrRepo.findAll().stream()
				.filter(rr -> Objects.equals(rr.getGroupId(), groupId)).toList();

		if (children.isEmpty()) {
			g.setStatus("Submitted");
			groupRepo.save(g);
			return;
		}
		boolean anyRejected = children.stream().anyMatch(rr -> "Rejected".equalsIgnoreCase(rr.getStatus()));
		boolean allApproved = !children.isEmpty()
				&& children.stream().allMatch(rr -> "Approved".equalsIgnoreCase(rr.getStatus()));

		if (anyRejected && !allApproved)
			g.setStatus("PartiallyApproved");
		else if (allApproved)
			g.setStatus("Approved");
		else
			g.setStatus("Submitted");
		groupRepo.save(g);
	}

	@Override
	public void saveResponseIntoTemplate(Long groupId, Map<String, Object> response) {
		ResourceRequestGroup g = groupRepo.findById(groupId)
				.orElseThrow(() -> new IllegalArgumentException("Group not found"));
		Map<String, Object> existing = g.getRequestTemplate();
		Map<String, Object> safe = om.convertValue(response, Map.class);
		Map<String, Object> merged = new LinkedHashMap<>();
		if (existing != null)
			merged.putAll(existing);
		merged.put("response", safe);
		g.setRequestTemplate(merged);
		groupRepo.save(g);
	}

	private void validateBulk(BulkCreateResReqDto dto) {
		if (dto.getProjectId() == null)
			throw new IllegalArgumentException("projectId is required");
		if (dto.getRequesterUserId() == null)
			throw new IllegalArgumentException("requesterUserId is required");
		if (dto.getCount() == null || dto.getCount() <= 0)
			throw new IllegalArgumentException("count must be > 0");
	}

	private void validateSkillIds(List<Long> skillIds) {
		if (skillIds == null || skillIds.isEmpty())
			return;
		for (Long id : skillIds) {
			if (id == null)
				continue;
			if (!skillRepo.existsById(id)) {
				throw new IllegalArgumentException("Invalid skillId: " + id + " not found in skill table");
			}
		}
	}

	private ResourceRequestGroupDto toGroupDto(ResourceRequestGroup g) {
		ResourceRequestGroupDto dto = new ResourceRequestGroupDto();
		dto.setGroupId(g.getGroupId());
		dto.setCompanyId(g.getCompanyId());
		dto.setCreatedBy(g.getCreatedBy());
		dto.setTitle(g.getTitle());
		dto.setTotalRequested(g.getTotalRequested());
		dto.setStatus(g.getStatus());
		dto.setCreatedAt(formatNow(g));

		Map<String, Object> tpl = g.getRequestTemplate();
		if (tpl == null)
			tpl = Map.of();

		dto.setExperienceRange(asText(tpl.get("experienceRange")));
		dto.setLocationType(asText(tpl.get("locationType")));
		dto.setWorkMode(asText(tpl.get("workMode")));
		dto.setLocation(asText(tpl.get("location")));
		dto.setPriority(asText(tpl.get("priority")));
		dto.setEstimatedCostTotal(asBigDecimal(tpl.get("estimatedCostTotal")));
		dto.setEstimatedCostPerResourceMonth(asBigDecimal(tpl.get("estimatedCostPerResourceMonth")));

		dto.setPrimarySkillIds(asLongList(tpl.get("primarySkillIds")));
		dto.setSecondarySkillIds(asLongList(tpl.get("secondarySkillIds")));
		dto.setSkillIds(asLongList(tpl.get("skillIds")));
		if (g.getCreatedAt() != null) {
			if ("Draft".equalsIgnoreCase(g.getStatus())) {
				dto.setDaysPending(
						java.time.temporal.ChronoUnit.DAYS.between(g.getCreatedAt(), java.time.OffsetDateTime.now()));
			}
			if (g.getHrApprovedAt() != null) {
				dto.setDaysToApprove(java.time.temporal.ChronoUnit.DAYS.between(g.getCreatedAt(), g.getHrApprovedAt()));
			}
		}
		return dto;
	}

	private String formatNow(ResourceRequestGroup g) {
		return g.getCreatedAt() == null ? null : g.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private void createNotif(Long userId, String title, String message, String priority, String type, Long entityId) {
		Notification n = new Notification();
		n.setUserId(userId);
		n.setTitle(title);
		n.setMessage(message);
		n.setPriority(priority);
		n.setRelatedEntityType(type);
		n.setRelatedEntityId(entityId);
		notificationRepo.save(n);
	}

	private static String asText(Object o) {
		return o == null ? null : o.toString();
	}

	private static BigDecimal asBigDecimal(Object o) {
		if (o == null)
			return null;
		if (o instanceof BigDecimal b)
			return b;
		if (o instanceof Number n)
			return new BigDecimal(n.toString());
		try {
			return new BigDecimal(o.toString());
		} catch (Exception e) {
			return null;
		}
	}

	private static List<Long> asLongList(Object o) {
		if (o == null)
			return null;
		if (o instanceof List<?> list) {
			return list.stream().map(item -> {
				if (item instanceof Number n)
					return n.longValue();
				try {
					return Long.parseLong(item.toString());
				} catch (Exception e) {
					return null;
				}
			}).filter(Objects::nonNull).collect(Collectors.toList());
		}
		return null;
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

	@Override
	@Transactional(readOnly = true)
	public Page<GroupFlowDto> getGroupFlowList(Long companyId, Long accountId, Long projectId, Long groupId,
			String fromDate, String toDate, Pageable pageable) {

		LocalDate from = parseFlexibleDate(fromDate);
		LocalDate to = parseFlexibleDate(toDate);

		final List<Long> accountProjectIds = (accountId == null) ? null
				: projectRepo.findAll().stream().filter(p -> Objects.equals(p.getAccountId(), accountId))
						.map(Project::getProjectId).toList();

		if (accountId != null && (accountProjectIds == null || accountProjectIds.isEmpty())) {
			return Page.empty(pageable);
		}

		Specification<ResourceRequestGroup> spec = (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();

			if (companyId != null) {
				p.add(cb.equal(root.get("companyId"), companyId));
			}
			if (groupId != null) {
				p.add(cb.equal(root.get("groupId"), groupId));
			}
			if (projectId != null) {
				p.add(cb.equal(root.get("projectId"), projectId));
			}
			if (accountProjectIds != null) {
				p.add(root.get("projectId").in(accountProjectIds));
			}
			if (from != null) {
				p.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
						from.atStartOfDay().atOffset(OffsetDateTime.now().getOffset())));
			}
			if (to != null) {
				p.add(cb.lessThanOrEqualTo(root.get("createdAt"),
						to.atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset())));
			}
			return cb.and(p.toArray(new Predicate[0]));
		};

		Page<ResourceRequestGroup> groupPage = groupRepo.findAll(spec, pageable);
		if (groupPage.isEmpty())
			return Page.empty(pageable);

		List<Long> groupIds = groupPage.getContent().stream().map(ResourceRequestGroup::getGroupId).toList();

		List<ResourceRequest> allRequests = rrRepo.findAllByGroupIdIn(groupIds);
		Map<Long, List<ResourceRequest>> reqsByGroupMap = allRequests.stream()
				.collect(Collectors.groupingBy(ResourceRequest::getGroupId));

		List<Long> allRequestIds = allRequests.stream().map(ResourceRequest::getRequestId).toList();

		List<Interview> allInterviews = interviewRepo.findAllByRequestIdIn(allRequestIds);
		Map<Long, List<Interview>> interviewsByReqMap = allInterviews.stream()
				.collect(Collectors.groupingBy(Interview::getRequestId));

		List<Allocation> allAllocations = allocationRepo.findByRequestIdIn(allRequestIds);
		Map<Long, Allocation> allocByReqMap = allAllocations.stream()
				.collect(Collectors.toMap(Allocation::getRequestId, Function.identity(), (a1, a2) -> a1));

		Map<Long, Map<String, Integer>> summaries = preCalculateSummaries(reqsByGroupMap, interviewsByReqMap,
				allocByReqMap);
		Set<Long> allEmployeeIds = allInterviews.stream().map(Interview::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		allAllocations.stream().map(Allocation::getEmployeeId).filter(Objects::nonNull).forEach(allEmployeeIds::add);

		Set<Long> allUserIds = groupPage.getContent().stream().map(ResourceRequestGroup::getCreatedBy) 
				.filter(Objects::nonNull).collect(Collectors.toSet());
		allInterviews.stream().flatMap(i -> readProgress(i.getLevelProgress()).stream())
				.map(LevelProgressDto::getInterviewerUserId).filter(Objects::nonNull).forEach(allUserIds::add);

		Map<Long, Employee> employeeMap = employeeRepo.findAllById(allEmployeeIds).stream()
				.collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

		Map<Long, EmployeeDocument> resumeMap = employeeDocumentRepo
				.findPrimaryResumesForEmployees(new ArrayList<>(allEmployeeIds)).stream()
				.collect(Collectors.toMap(EmployeeDocument::getEmployeeId, Function.identity()));

		Map<Long, UserAccount> userMap = userAccountRepo.findAllById(allUserIds).stream()
				.collect(Collectors.toMap(UserAccount::getUserId, Function.identity()));

		Set<Long> userEmployeeIds = userMap.values().stream().map(UserAccount::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		employeeRepo.findAllById(userEmployeeIds).stream()
				.forEach(emp -> employeeMap.putIfAbsent(emp.getEmployeeId(), emp));

		Map<Long, Company> companyMap = companyRepo
				.findAllById(groupPage.getContent().stream().map(ResourceRequestGroup::getCompanyId).toList()).stream()
				.collect(Collectors.toMap(Company::getCompanyId, Function.identity()));

		Map<Long, Project> projectMap = projectRepo
				.findAllById(groupPage.getContent().stream().map(ResourceRequestGroup::getProjectId).toList()).stream()
				.collect(Collectors.toMap(Project::getProjectId, Function.identity()));

		Set<Long> accountIds = projectMap.values().stream().map(Project::getAccountId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Account> accountMap = accountRepo.findAllById(accountIds).stream()
				.collect(Collectors.toMap(Account::getAccountId, Function.identity()));

		List<GroupFlowDto> flatRows = new ArrayList<>();

		for (ResourceRequestGroup group : groupPage.getContent()) {
			buildFlatFlowRows(flatRows, group, reqsByGroupMap.getOrDefault(group.getGroupId(), Collections.emptyList()),
					summaries.getOrDefault(group.getGroupId(), Collections.emptyMap()), interviewsByReqMap,
					allocByReqMap, employeeMap, resumeMap, userMap, companyMap, projectMap, accountMap);
		}

		return new PageImpl<>(flatRows, pageable, groupPage.getTotalElements());
	}

	private Map<Long, Map<String, Integer>> preCalculateSummaries(
			Map<Long, List<ResourceRequest>> reqsByGroupMap,
			Map<Long, List<Interview>> interviewsByReqMap,
			Map<Long, Allocation> allocByReqMap) {

		Map<Long, Map<String, Integer>> summaryMap = new LinkedHashMap<>();

		for (Map.Entry<Long, List<ResourceRequest>> entry : reqsByGroupMap.entrySet()) {
			Long groupId = entry.getKey();
			List<ResourceRequest> requests = entry.getValue();

			int open = 0;
			int interviewing = 0;
			int selected = 0;
			int allocated = 0;
			int onboarded = 0;
			int rejected = 0;
			int totalInterviewsCount = 0;

			for (ResourceRequest req : requests) {
				Long reqId = req.getRequestId();
				List<Interview> reqInterviews = interviewsByReqMap.getOrDefault(reqId, List.of());
				totalInterviewsCount += reqInterviews.size();

				boolean hasAnyInterview = !reqInterviews.isEmpty();
				Allocation reqAlloc = allocByReqMap.get(reqId);
				boolean hasAllocation = (reqAlloc != null);

				Interview latestInterview = reqInterviews.isEmpty()
						? null
						: reqInterviews.stream()
								.max(Comparator.comparing(Interview::getInterviewId,
										Comparator.nullsLast(Comparator.naturalOrder())))
								.orElse(null);

				boolean anySelectedForRequest = false;
				boolean anyOnboardedForRequest = false;
				boolean anyRejectedInterview = false;

				if (latestInterview != null) {
					String st = latestInterview.getStatus();
					if ("Selected".equalsIgnoreCase(st)) {
						anySelectedForRequest = true;
					} else if ("Rejected".equalsIgnoreCase(st)) {
						anyRejectedInterview = true;
					}

					List<LevelProgressDto> latestLevels = readProgress(latestInterview.getLevelProgress());
					anyOnboardedForRequest = latestLevels.stream().anyMatch(lp ->
							"ONBOARDING".equalsIgnoreCase(lp.getLevel())
									&& lp.getStatus() != null
									&& lp.getStatus().replace(" ", "").equalsIgnoreCase("OnBoarded"));
				}

				boolean isRejectedReq = "Rejected".equalsIgnoreCase(req.getStatus())
						|| "Cancelled".equalsIgnoreCase(req.getStatus());

				if (hasAllocation) {
					allocated++;
				}

				boolean isFinalPositive = anySelectedForRequest || anyOnboardedForRequest || hasAllocation;
				boolean isFinalNegative = anyRejectedInterview || isRejectedReq;

				if (!isFinalPositive && isFinalNegative) {
					rejected++;
				}

				if (!isFinalPositive && !isFinalNegative) {
					if (hasAnyInterview) {
						interviewing++;
					} else {
						open++;
					}
				}

				for (Interview iv : reqInterviews) {
					boolean interviewSelected = "Selected".equalsIgnoreCase(iv.getStatus());

					List<LevelProgressDto> levels = readProgress(iv.getLevelProgress());
					boolean interviewOnboarded = levels.stream().anyMatch(lp ->
							"ONBOARDING".equalsIgnoreCase(lp.getLevel())
									&& lp.getStatus() != null
									&& lp.getStatus().replace(" ", "").equalsIgnoreCase("OnBoarded"));

					boolean allocationForThisCandidate = false;
					if (reqAlloc != null && iv.getEmployeeId() != null) {
						allocationForThisCandidate = Objects.equals(reqAlloc.getEmployeeId(), iv.getEmployeeId());
					}

					if (interviewOnboarded || allocationForThisCandidate) {
						onboarded++;
					}

					if (interviewSelected || interviewOnboarded || allocationForThisCandidate) {
						selected++;
					}
				}
			}

			summaryMap.put(groupId, Map.of(
					"totalRequests", requests.size(),
					"open", open,
					"interviewing", interviewing,
					"selected", selected,
					"allocated", allocated,
					"onboarded", onboarded,
					"rejected", rejected,
					"totalInterviews", totalInterviewsCount
			));
		}

		return summaryMap;
	}

	private void buildFlatFlowRows(List<GroupFlowDto> flatRows, ResourceRequestGroup group,
			List<ResourceRequest> childRequests, Map<String, Integer> summary,
			Map<Long, List<Interview>> interviewsByReqMap, Map<Long, Allocation> allocByReqMap,
			Map<Long, Employee> employeeMap, Map<Long, EmployeeDocument> resumeMap, Map<Long, UserAccount> userMap,
			Map<Long, Company> companyMap, Map<Long, Project> projectMap, Map<Long, Account> accountMap) {

		GroupFlowDto baseRow = new GroupFlowDto();

		baseRow.setGroupId(group.getGroupId());
		baseRow.setGroupTitle(group.getTitle());
		baseRow.setGroupCreatedAt(group.getCreatedAt());
		baseRow.setGroupTotalRequested(group.getTotalRequested());
		baseRow.setGroupStatus(group.getStatus());
		if (group.getCreatedBy() != null) {
			baseRow.setGroupCreatorUserId(group.getCreatedBy());
			UserAccount creator = userMap.get(group.getCreatedBy());
			if (creator != null) {
				baseRow.setGroupCreatorEmail(creator.getEmail());
				if (creator.getEmployeeId() != null) {
					Employee emp = employeeMap.get(creator.getEmployeeId());
					if (emp != null)
						baseRow.setGroupCreatorName(emp.getFirstName() + " " + emp.getLastName());
				}
			}
		}

		baseRow.setCompanyId(group.getCompanyId());
		companyMap.computeIfPresent(group.getCompanyId(), (k, v) -> {
			baseRow.setCompanyName(v.getCompanyName());
			return v;
		});
		baseRow.setProjectId(group.getProjectId());
		Project proj = projectMap.get(group.getProjectId());
		if (proj != null) {
			baseRow.setProjectName(proj.getProjectName());
			if (proj.getAccountId() != null) {
				baseRow.setAccountId(proj.getAccountId());
				accountMap.computeIfPresent(proj.getAccountId(), (k, v) -> {
					baseRow.setAccountName(v.getAccountName());
					return v;
				});
			}
		}

		baseRow.setSummaryTotalRequests(summary.getOrDefault("totalRequests", 0));
		baseRow.setSummaryOpen(summary.getOrDefault("open", 0));
		baseRow.setSummaryInterviewing(summary.getOrDefault("interviewing", 0));
		baseRow.setSummarySelected(summary.getOrDefault("selected", 0));
		baseRow.setSummaryAllocated(summary.getOrDefault("allocated", 0));
		baseRow.setSummaryOnboarded(summary.getOrDefault("onboarded", 0));
		baseRow.setSummaryRejected(summary.getOrDefault("rejected", 0));
		baseRow.setSummaryTotalInterviews(summary.getOrDefault("totalInterviews", 0));

		long pendingDays = (group.getCreatedAt() != null)
				? ChronoUnit.DAYS.between(group.getCreatedAt().toLocalDate(), LocalDate.now())
				: 0;
		baseRow.setSummaryPendingDays(pendingDays);

		if (childRequests.isEmpty()) {
			flatRows.add(baseRow);
			return;
		}

		for (ResourceRequest req : childRequests) {
			GroupFlowDto reqRow = copyBaseRow(baseRow);
			reqRow.setRequestId(req.getRequestId());
			reqRow.setRequestStatus(req.getStatus());

			List<Interview> interviewsForReq = interviewsByReqMap.getOrDefault(req.getRequestId(),
					Collections.emptyList());

			if (interviewsForReq.isEmpty()) {
				flatRows.add(reqRow);
				continue;
			}

			for (Interview interview : interviewsForReq) {
				GroupFlowDto interviewRow = copyBaseRow(reqRow);
				interviewRow.setInterviewId(interview.getInterviewId());
				interviewRow.setInterviewOverallStatus(interview.getStatus());

				if (interview.getEmployeeId() != null) {
					interviewRow.setResourceType("EMPLOYEE");
					interviewRow.setCandidateEmployeeId(interview.getEmployeeId());
					Employee candidate = employeeMap.get(interview.getEmployeeId());
					if (candidate != null) {
						interviewRow.setCandidateName(candidate.getFirstName() + " " + candidate.getLastName());
						interviewRow.setCandidateEmail(candidate.getEmail());
						interviewRow.setCandidatePhoneNumber(candidate.getPhoneNumber());
						interviewRow.setCandidateDesignation(candidate.getJobTitle());
						interviewRow.setCandidateExperience(candidate.getExperienceYears());
						
						EmployeeDocument resume = resumeMap.get(candidate.getEmployeeId());
						if (resume != null) {
							interviewRow.setCandidateResumeStatus(resume.getResumeShareStatus());
						}
					}
				} else if (interview.getCandidateId() != null) {
					interviewRow.setResourceType("CANDIDATE");
					interviewRow.setCandidateId(interview.getCandidateId());
					
					candidateRepo.findById(interview.getCandidateId()).ifPresent(cand -> {
						interviewRow.setCandidateName(cand.getFirstName() + " " + cand.getLastName());
						interviewRow.setCandidateEmail(cand.getEmail());
						interviewRow.setCandidatePhoneNumber(cand.getPhoneNumber());
						interviewRow.setCandidateDesignation(null);
						interviewRow.setCandidateExperience(cand.getExperienceYears());
						
						candidateDocumentRepo.findPrimaryResume(interview.getCandidateId()).ifPresent(doc -> {
							interviewRow.setCandidateResumeStatus(doc.getResumeShareStatus());
						});
					});
				}

				Allocation alloc = allocByReqMap.get(req.getRequestId());
				
				if (alloc != null) {
					boolean match = (interview.getEmployeeId() != null && Objects.equals(alloc.getEmployeeId(), interview.getEmployeeId()))
							|| (interview.getCandidateId() != null && Objects.equals(alloc.getCandidateId(), interview.getCandidateId()));

					if (match) {
						interviewRow.setAllocationId(alloc.getAllocationId());
						
						if(alloc.getEmployeeId() != null) {
							Employee allocEmp = employeeMap.get(alloc.getEmployeeId());
							interviewRow.setAllocationEmployeeName(allocEmp != null ? allocEmp.getFirstName() + " " + allocEmp.getLastName() : "Internal");
						} else if(alloc.getCandidateId() != null) {
							interviewRow.setAllocationEmployeeName(interviewRow.getCandidateName());
						}
						
						interviewRow.setAllocationStartDate(alloc.getStartDate());
						interviewRow.setAllocationEndDate(alloc.getEndDate());
						interviewRow.setAllocationProjectRole(alloc.getProjectRole());
						interviewRow.setAllocationIsBillable(alloc.getIsBillable());
					}
				}

				List<LevelProgressDto> levels = readProgress(interview.getLevelProgress());

				if (levels.isEmpty() || levels.stream().allMatch(l -> "__META__".equals(l.getLevel()))) {
					flatRows.add(interviewRow);
					continue;
				}

				for (LevelProgressDto level : levels) {
					if ("__META__".equals(level.getLevel()))
						continue;

					GroupFlowDto levelRow = copyBaseRow(interviewRow);
					levelRow.setInterviewLevel(level.getLevel());
					levelRow.setInterviewLevelStatus(level.getStatus());
					levelRow.setInterviewNotes(level.getInterviewNotes());
					levelRow.setInterviewCompletedAt(level.getCompletedAt());

					if (level.getInterviewerUserId() != null) {
						levelRow.setInterviewerUserId(level.getInterviewerUserId());
						UserAccount user = userMap.get(level.getInterviewerUserId());
						if (user != null) {
							levelRow.setInterviewerEmail(user.getEmail());
							if (user.getEmployeeId() != null) {
								Employee emp = employeeMap.get(user.getEmployeeId());
								if (emp != null)
									levelRow.setInterviewerName(emp.getFirstName() + " " + emp.getLastName());
							}
							if (levelRow.getInterviewerName() == null) {
								levelRow.setInterviewerName(user.getEmail());
							}
						}
					}

					flatRows.add(levelRow);
				}
			}
		}
	}

	private GroupFlowDto copyBaseRow(GroupFlowDto base) {
		GroupFlowDto copy = new GroupFlowDto();

		copy.setGroupId(base.getGroupId());
		copy.setGroupTitle(base.getGroupTitle());
		copy.setGroupCreatedAt(base.getGroupCreatedAt());
		copy.setGroupTotalRequested(base.getGroupTotalRequested());
		copy.setGroupStatus(base.getGroupStatus());
		copy.setGroupCreatorUserId(base.getGroupCreatorUserId());
		copy.setGroupCreatorName(base.getGroupCreatorName());
		copy.setGroupCreatorEmail(base.getGroupCreatorEmail());

		copy.setCompanyId(base.getCompanyId());
		copy.setCompanyName(base.getCompanyName());
		copy.setProjectId(base.getProjectId());
		copy.setProjectName(base.getProjectName());
		copy.setAccountId(base.getAccountId());
		copy.setAccountName(base.getAccountName());

		copy.setSummaryTotalRequests(base.getSummaryTotalRequests());
		copy.setSummaryOpen(base.getSummaryOpen());
		copy.setSummaryInterviewing(base.getSummaryInterviewing());
		copy.setSummarySelected(base.getSummarySelected());
		copy.setSummaryAllocated(base.getSummaryAllocated());
		copy.setSummaryOnboarded(base.getSummaryOnboarded());
		copy.setSummaryRejected(base.getSummaryRejected());
		copy.setSummaryTotalInterviews(base.getSummaryTotalInterviews());
		copy.setSummaryPendingDays(base.getSummaryPendingDays());

		copy.setRequestId(base.getRequestId());
		copy.setRequestStatus(base.getRequestStatus());

		copy.setInterviewId(base.getInterviewId());
		copy.setInterviewOverallStatus(base.getInterviewOverallStatus());

		copy.setResourceType(base.getResourceType());
		copy.setCandidateId(base.getCandidateId());
		copy.setCandidateEmployeeId(base.getCandidateEmployeeId());
		copy.setCandidateName(base.getCandidateName());
		copy.setCandidateEmail(base.getCandidateEmail());
		copy.setCandidatePhoneNumber(base.getCandidatePhoneNumber());
		copy.setCandidateResumeStatus(base.getCandidateResumeStatus());
		copy.setCandidateDesignation(base.getCandidateDesignation());
		copy.setCandidateExperience(base.getCandidateExperience());

		copy.setAllocationId(base.getAllocationId());
		copy.setAllocationEmployeeName(base.getAllocationEmployeeName());
		copy.setAllocationStartDate(base.getAllocationStartDate());
		copy.setAllocationEndDate(base.getAllocationEndDate());
		copy.setAllocationProjectRole(base.getAllocationProjectRole());
		copy.setAllocationIsBillable(base.getAllocationIsBillable());

		return copy;
	}

	private List<LevelProgressDto> readProgress(String json) {
		try {
			if (json == null || json.isBlank())
				return new ArrayList<>();
			return om.readValue(json, new TypeReference<List<LevelProgressDto>>() {
			});
		} catch (Exception e) {
			log.error("Failed to parse level progress JSON: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	private LocalDate parseFlexibleDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}

		List<DateTimeFormatter> formatters = Arrays.asList(DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"),
				DateTimeFormatter.ofPattern("MM/dd/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd"));

		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.parse(dateStr, formatter);
			} catch (DateTimeParseException e) {
			}
		}
		log.warn("Could not parse date string: {}", dateStr);
		return null;
	}
}
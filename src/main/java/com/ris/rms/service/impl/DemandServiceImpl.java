package com.ris.rms.service.impl;

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
import java.util.Optional;
import java.util.Set;
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
import com.ris.rms.dto.DemandCreateDto;
import com.ris.rms.dto.DemandRequestSummaryDto;
import com.ris.rms.dto.DemandResponseDto;
import com.ris.rms.dto.DemandStageCountsDto;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.dto.ResourceRequestDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Allocation;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;
import com.ris.rms.entity.Interview;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.repository.EmployeeDocumentRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.InterviewRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.DemandService;
import com.ris.rms.service.ResourceRequestService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DemandServiceImpl implements DemandService {

	private final DemandRepository demandRepo;
	private final ResourceRequestService rrService;
	private final ResourceRequestRepository rrRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final DepartmentRepository departmentRepo;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final SkillRepository skillRepo;
	private final InterviewRepository interviewRepo;
	private final AllocationRepository allocationRepo;
	private final EmployeeDocumentRepository employeeDocumentRepo;

	private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Override
	public DemandResponseDto createDemand(DemandCreateDto dto) {
		companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found: " + dto.getCompanyId()));
		accountRepo.findById(dto.getAccountId())
				.orElseThrow(() -> new IllegalArgumentException("Account not found: " + dto.getAccountId()));
		departmentRepo.findById(dto.getDepartmentId())
				.orElseThrow(() -> new IllegalArgumentException("Department not found: " + dto.getDepartmentId()));
		userAccountRepo.findById(dto.getRequesterUserId()).orElseThrow(
				() -> new IllegalArgumentException("Requester user not found: " + dto.getRequesterUserId()));

		Demand demand = new Demand();
		demand.setCompanyId(dto.getCompanyId());
		demand.setRequesterUserId(dto.getRequesterUserId());
		demand.setAccountId(dto.getAccountId());
		demand.setDepartmentId(dto.getDepartmentId());
		demand.setProjectName(dto.getProjectName());
		demand.setDemandtitle(dto.getDemandTitle());
		demand.setYearsofexp(dto.getYearsofexp());
		demand.setSkillIds(dto.getSkillIds());
		demand.setRoleduration(dto.getRoleDuration());
		demand.setWorklocpref(dto.getWorkLocPref());
		demand.setPriority(dto.getPriority());
		demand.setLocationType(dto.getLocationType());
		demand.setWorkMode(dto.getWorkMode());
		demand.setResourceRequestsCount(dto.getResourceRequests());
		demand.setOverallStatus("Pending");

		Demand savedDemand = demandRepo.save(demand);

		for (int i = 0; i < dto.getResourceRequests(); i++) {
			ResourceRequestDto childDto = new ResourceRequestDto();

			childDto.setProjectId(null);
			childDto.setRequesterUserId(dto.getRequesterUserId());
			childDto.setNumberOfResources(1);
			childDto.setDemandId(savedDemand.getDemandid());

			childDto.setExperienceRange(dto.getYearsofexp());
			childDto.setSkillIds(dto.getSkillIds());
			childDto.setLocation(dto.getWorkLocPref());
			childDto.setPriority(dto.getPriority());
			childDto.setLocationType(dto.getLocationType());
			childDto.setWorkMode(dto.getWorkMode());

			try {
				rrService.create(childDto);
			} catch (Exception e) {
				log.error("Failed to create child resource request for demand {}: {}", savedDemand.getDemandid(),
						e.getMessage());
			}
		}

		return getDemandById(savedDemand.getDemandid());
	}

	@Override
	@Transactional(readOnly = true)
	public DemandResponseDto getDemandById(Long demandId) {
		Demand demand = demandRepo.findById(demandId)
				.orElseThrow(() -> new IllegalArgumentException("Demand not found: " + demandId));
		return toResponseDto(demand, true);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DemandResponseDto> listDemands(Long companyId, Long accountId, Long departmentId, String status,
			Integer page, Integer size) { // Added page and size
		Specification<Demand> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (companyId != null) {
				predicates.add(cb.equal(root.get("companyId"), companyId));
			}
			if (accountId != null) {
				predicates.add(cb.equal(root.get("accountId"), accountId));
			}
			if (departmentId != null) {
				predicates.add(cb.equal(root.get("departmentId"), departmentId));
			}
			if (status != null && !status.isBlank()) {
				predicates.add(cb.equal(root.get("overallStatus"), status));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		Sort sort = Sort.by(Sort.Direction.DESC, "createddt");

		int pageNum = (page != null && page >= 0) ? page : 0;
		int pageSize = (size != null && size > 0) ? size : 20;

		Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

		Page<Demand> demandPage = demandRepo.findAll(spec, pageable);

		return demandPage.getContent().stream().map(demand -> toResponseDto(demand, false)).toList();
	}

	private DemandResponseDto toResponseDto(Demand demand) {
		return toResponseDto(demand, false);
	}

	private DemandResponseDto toResponseDto(Demand demand, boolean recalculateStatus) {
		DemandResponseDto dto = new DemandResponseDto();

		dto.setDemandid(demand.getDemandid());
		dto.setDemandTitle(demand.getDemandtitle());
		dto.setDemandOpenDt(demand.getDemandopendt());
		dto.setCompanyId(demand.getCompanyId());
		dto.setAccountId(demand.getAccountId());
		dto.setDepartmentId(demand.getDepartmentId());
		dto.setProjectName(demand.getProjectName());
		dto.setRequesterUserId(demand.getRequesterUserId());
		dto.setYearsofexp(demand.getYearsofexp());
		dto.setSkillIds(demand.getSkillIds());
		dto.setFulfilmentDt(demand.getFulfilmentdt());
		dto.setRoleDuration(demand.getRoleduration());
		dto.setLocationType(demand.getLocationType());
		dto.setWorkLocPref(demand.getWorklocpref());
		dto.setWorkMode(demand.getWorkMode());
		dto.setPriority(demand.getPriority());
		dto.setOverallStatus(demand.getOverallStatus());
		dto.setCreateddt(demand.getCreateddt());
		dto.setUpdateddt(demand.getUpdateddt());
		dto.setResourceRequestsCount(demand.getResourceRequestsCount());
		dto.setPendingDays(ChronoUnit.DAYS.between(demand.getCreateddt().toLocalDate(), LocalDate.now()));

		companyRepo.findById(demand.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
		accountRepo.findById(demand.getAccountId()).ifPresent(a -> {
			dto.setAccountName(a.getAccountName());
			dto.setAccountEmail(a.getContactPersonEmail());
		});
		departmentRepo.findById(demand.getDepartmentId()).ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
		if (demand.getSkillIds() != null && !demand.getSkillIds().isEmpty()) {
			dto.setSkillName(skillRepo.findAllById(demand.getSkillIds()).stream().map(Skill::getSkillName).toList());
		}
		userAccountRepo.findById(demand.getRequesterUserId()).ifPresent(ua -> {
			dto.setRequesterEmail(ua.getEmail());
			if (ua.getEmployeeId() != null) {
				employeeRepo.findById(ua.getEmployeeId())
						.ifPresent(e -> dto.setRequesterName(e.getFirstName() + " " + e.getLastName()));
			}
		});

		List<ResourceRequest> childRequests = rrRepo.findAllByDemandId(demand.getDemandid());

		List<DemandRequestSummaryDto> summaryList = new ArrayList<>();
		DemandStageCountsDto stageCounts = new DemandStageCountsDto();
		stageCounts.setTotal(childRequests.size());

		int completedOrRejectedCount = 0;
		LocalDate lastFulfilmentDate = null;

		for (ResourceRequest req : childRequests) {
			DemandRequestSummaryDto summaryItem = new DemandRequestSummaryDto();
			summaryItem.setRequestId(req.getRequestId());

			OffsetDateTime lastUpdate = req.getSubmittedDate() != null
					? req.getSubmittedDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset())
					: demand.getCreateddt();
			summaryItem.setPendingDays(ChronoUnit.DAYS.between(lastUpdate.toLocalDate(), LocalDate.now()));

			boolean isFinalState = false;

			Optional<Allocation> alloc =
			        allocationRepo.findFirstByRequestIdOrderByStartDateDesc(req.getRequestId());
			if (alloc.isPresent()) {
				summaryItem.setStage("Allocated");
				summaryItem.setStageReason("Employee allocated on " + alloc.get().getStartDate());
				summaryItem.setLastUpdatedAt(
						alloc.get().getStartDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
				stageCounts.addAllocated();
				completedOrRejectedCount++;
				isFinalState = true;
				if (lastFulfilmentDate == null || alloc.get().getStartDate().isAfter(lastFulfilmentDate)) {
					lastFulfilmentDate = alloc.get().getStartDate();
				}
			}

			if (!isFinalState) {
				Optional<Interview> interviewOpt = interviewRepo
						.findTopByRequestIdOrderByInterviewIdDesc(req.getRequestId());
				if (interviewOpt.isPresent()) {
					Interview interview = interviewOpt.get();
					lastUpdate = interview.getScheduledAt();

					if ("Selected".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Selected");
						summaryItem.setStageReason("Interview Selected, Pending Allocation");
						stageCounts.addSelected();
					} else if ("Rejected".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Rejected");
						summaryItem.setStageReason("Interview Rejected");
						stageCounts.addRejected();
						completedOrRejectedCount++;
						isFinalState = true;
					} else if ("Scheduled".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Interview Scheduled");
						summaryItem.setStageReason("Interview Scheduled");
						stageCounts.addInterviewScheduled();
					} else {
						summaryItem.setStage("Interview In Progress");
						summaryItem.setStageReason("Interview " + interview.getStatus());
						stageCounts.addInterviewInProgress();
					}
				}
			}

			if (summaryItem.getStage() == null) {
				if ("Fulfilled".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Allocated");
					summaryItem.setStageReason("Request fulfilled");
					stageCounts.addAllocated();
					completedOrRejectedCount++;
					isFinalState = true;
				} else if ("Rejected".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Rejected");
					summaryItem.setStageReason("Request Rejected by HR/PM");
					stageCounts.addRejected();
					completedOrRejectedCount++;
					isFinalState = true;
				} else if ("Cancelled".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Rejected");
					summaryItem.setStageReason("Request Cancelled");
					stageCounts.addRejected();
					completedOrRejectedCount++;
					isFinalState = true;
				} else if ("Submitted".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Approval Pending");
					summaryItem.setStageReason("Awaiting HR approval");
					stageCounts.addApprovalPending();
				} else {
					summaryItem.setStage("Open");
					summaryItem.setStageReason("Draft state or no interview yet");
					stageCounts.addOpen();
				}
			}

			summaryItem.setLastUpdatedAt(lastUpdate);
			summaryList.add(summaryItem);
		}

		dto.setRequestsSummary(summaryList);
		dto.setStageCounts(stageCounts);

		if (recalculateStatus || !demand.getOverallStatus().equals("Completed")) {
			if (completedOrRejectedCount == demand.getResourceRequestsCount()
					&& demand.getResourceRequestsCount() > 0) {
				dto.setOverallStatus("Completed");
				dto.setFulfilmentDt(lastFulfilmentDate != null ? lastFulfilmentDate : LocalDate.now());

				demand.setOverallStatus("Completed");
				demand.setFulfilmentdt(dto.getFulfilmentDt());
				demandRepo.save(demand);
			} else {
				dto.setOverallStatus("Pending");
			}
		}

		return dto;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<GroupFlowDto> getDemandFlowList(Long companyId, Long accountId, Long departmentId, String status,
			String fromDate, String toDate, Pageable pageable) {

		LocalDate from = parseFlexibleDate(fromDate);
		LocalDate to = parseFlexibleDate(toDate);

		Specification<Demand> spec = (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();
			if (companyId != null) {
				p.add(cb.equal(root.get("companyId"), companyId));
			}
			if (accountId != null) {
				p.add(cb.equal(root.get("accountId"), accountId));
			}
			if (departmentId != null) {
				p.add(cb.equal(root.get("departmentId"), departmentId));
			}
			if (status != null && !status.isBlank()) {
				p.add(cb.equal(root.get("overallStatus"), status));
			}
			if (from != null) {
				p.add(cb.greaterThanOrEqualTo(root.get("demandopendt"), from));
			}
			if (to != null) {
				p.add(cb.lessThanOrEqualTo(root.get("demandopendt"), to));
			}
			return cb.and(p.toArray(new Predicate[0]));
		};

		Page<Demand> demandPage = demandRepo.findAll(spec, pageable);
		if (demandPage.isEmpty()) {
			return Page.empty(pageable);
		}

		List<Demand> demands = demandPage.getContent().stream()
				.sorted(Comparator.comparing(Demand::getDemandopendt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Demand::getDemandid, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Long> demandIds = demands.stream().map(Demand::getDemandid).toList();

		List<ResourceRequest> allRequests = rrRepo.findAll().stream()
				.filter(rr -> rr.getDemandId() != null && demandIds.contains(rr.getDemandId())).toList();

		Map<Long, List<ResourceRequest>> reqsByDemandMap = allRequests.stream()
				.collect(Collectors.groupingBy(ResourceRequest::getDemandId));

		List<Long> allRequestIds = allRequests.stream().map(ResourceRequest::getRequestId).toList();

		Map<Long, Map<String, Integer>> summaries = preCalculateDemandSummaries(allRequestIds, reqsByDemandMap);

		List<Interview> allInterviews = allRequestIds.isEmpty() ? List.of()
				: interviewRepo.findAllByRequestIdIn(allRequestIds);
		Map<Long, List<Interview>> interviewsByReqMap = allInterviews.stream()
				.collect(Collectors.groupingBy(Interview::getRequestId));

		List<Allocation> allAllocations = allRequestIds.isEmpty() ? List.of()
				: allocationRepo.findByRequestIdIn(allRequestIds);
		Map<Long, Allocation> allocByReqMap = allAllocations.stream()
				.collect(Collectors.toMap(Allocation::getRequestId, Function.identity(), (a1, a2) -> a1));

		Set<Long> allEmployeeIds = allInterviews.stream().map(Interview::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		allAllocations.stream().map(Allocation::getEmployeeId).filter(Objects::nonNull).forEach(allEmployeeIds::add);

		Map<Long, Employee> employeeMap = allEmployeeIds.isEmpty() ? Map.of()
				: employeeRepo.findAllById(allEmployeeIds).stream()
						.collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

		Map<Long, EmployeeDocument> resumeMap = allEmployeeIds.isEmpty() ? Map.of()
				: employeeDocumentRepo.findPrimaryResumesForEmployees(new ArrayList<>(allEmployeeIds)).stream()
						.collect(Collectors.toMap(EmployeeDocument::getEmployeeId, Function.identity()));

		Set<Long> allUserIds = new java.util.HashSet<>();
		allInterviews.stream().flatMap(i -> readProgress(i.getLevelProgress()).stream())
				.map(LevelProgressDto::getInterviewerUserId).filter(Objects::nonNull).forEach(allUserIds::add);

		Map<Long, UserAccount> userMap = allUserIds.isEmpty() ? Map.of()
				: userAccountRepo.findAllById(allUserIds).stream()
						.collect(Collectors.toMap(UserAccount::getUserId, Function.identity()));

		Set<Long> userEmployeeIds = userMap.values().stream().map(UserAccount::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (!userEmployeeIds.isEmpty()) {
			employeeRepo.findAllById(userEmployeeIds).forEach(emp -> employeeMap.putIfAbsent(emp.getEmployeeId(), emp));
		}

		Set<Long> companyIds = demands.stream().map(Demand::getCompanyId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Company> companyMap = companyIds.isEmpty() ? Map.of()
				: companyRepo.findAllById(companyIds).stream()
						.collect(Collectors.toMap(Company::getCompanyId, Function.identity()));

		Set<Long> accountIds = demands.stream().map(Demand::getAccountId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Account> accountMap = accountIds.isEmpty() ? Map.of()
				: accountRepo.findAllById(accountIds).stream()
						.collect(Collectors.toMap(Account::getAccountId, Function.identity()));

		List<GroupFlowDto> flatRows = new ArrayList<>();

		for (Demand d : demands) {
			buildFlatDemandFlowRows(flatRows, d, reqsByDemandMap.getOrDefault(d.getDemandid(), Collections.emptyList()),
					summaries.getOrDefault(d.getDemandid(), Collections.emptyMap()), interviewsByReqMap, allocByReqMap,
					employeeMap, resumeMap, userMap, companyMap, accountMap);
		}

		return new PageImpl<>(flatRows, pageable, demandPage.getTotalElements());
	}


	private Map<Long, Map<String, Integer>> preCalculateDemandSummaries(List<Long> allRequestIds,
			Map<Long, List<ResourceRequest>> reqsByDemandMap) {

		Map<Long, String> requestStatusMap = new LinkedHashMap<>();

		Map<Long, Allocation> allocMap = allRequestIds.isEmpty() ? Map.of()
				: allocationRepo.findByRequestIdIn(allRequestIds).stream()
						.collect(Collectors.toMap(Allocation::getRequestId, Function.identity(), (a1, a2) -> a1));
		allocMap.keySet().forEach(reqId -> requestStatusMap.put(reqId, "Allocated"));

		List<Long> nonAllocatedRequestIds = allRequestIds.stream().filter(id -> !allocMap.containsKey(id)).toList();

		if (!nonAllocatedRequestIds.isEmpty()) {
			interviewRepo.findAllByRequestIdIn(nonAllocatedRequestIds).stream()
					.collect(Collectors.groupingBy(Interview::getRequestId)).forEach((reqId, interviews) -> {
						if (interviews.stream().anyMatch(i -> "Rejected".equalsIgnoreCase(i.getStatus()))) {
							requestStatusMap.put(reqId, "Rejected");
						} else {
							requestStatusMap.put(reqId, "Interviewing");
						}
					});
		}

		Map<Long, Map<String, Integer>> summaryMap = new LinkedHashMap<>();
		for (Map.Entry<Long, List<ResourceRequest>> entry : reqsByDemandMap.entrySet()) {
			Long demandId = entry.getKey();
			List<ResourceRequest> requests = entry.getValue();

			int open = 0, interviewing = 0, allocated = 0, rejected = 0;
			for (ResourceRequest req : requests) {
				String status = requestStatusMap.get(req.getRequestId());
				if (status == null) {
					if ("Rejected".equalsIgnoreCase(req.getStatus()) || "Cancelled".equalsIgnoreCase(req.getStatus())) {
						status = "Rejected";
					} else {
						status = "Open";
					}
				}

				switch (status) {
				case "Allocated":
					allocated++;
					break;
				case "Interviewing":
					interviewing++;
					break;
				case "Rejected":
					rejected++;
					break;
				default:
					open++;
					break;
				}
			}
			summaryMap.put(demandId, Map.of("totalRequests", requests.size(), "open", open, "interviewing",
					interviewing, "allocated", allocated, "rejected", rejected));
		}
		return summaryMap;
	}

	private void buildFlatDemandFlowRows(List<GroupFlowDto> flatRows, Demand demand,
			List<ResourceRequest> childRequests, Map<String, Integer> summary,
			Map<Long, List<Interview>> interviewsByReqMap, Map<Long, Allocation> allocByReqMap,
			Map<Long, Employee> employeeMap, Map<Long, EmployeeDocument> resumeMap, Map<Long, UserAccount> userMap,
			Map<Long, Company> companyMap, Map<Long, Account> accountMap) {

		GroupFlowDto baseRow = new GroupFlowDto();

		baseRow.setGroupId(demand.getDemandid());
		baseRow.setGroupTitle(demand.getDemandtitle());
		OffsetDateTime openAt = demand.getDemandopendt() == null ? null
				: demand.getDemandopendt().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
		baseRow.setGroupCreatedAt(openAt);
		baseRow.setGroupTotalRequested(demand.getResourceRequestsCount());
		baseRow.setGroupStatus(demand.getOverallStatus());
		  baseRow.setDemandOpenDt(demand.getDemandopendt());
		    baseRow.setPriority(demand.getPriority());
		    baseRow.setRoleDuration(demand.getRoleduration());
		    LocalDate fulfilmentDt = null;

		    for (ResourceRequest req : childRequests) {
		        Allocation alloc = allocByReqMap.get(req.getRequestId());
		        if (alloc != null && alloc.getStartDate() != null) {
		            if (fulfilmentDt == null || alloc.getStartDate().isAfter(fulfilmentDt)) {
		                fulfilmentDt = alloc.getStartDate();
		            }
		        }
		    }

		    if (fulfilmentDt == null) {
		        fulfilmentDt = demand.getFulfilmentdt();
		    }
		    baseRow.setFulfilmentDt(fulfilmentDt); 
		baseRow.setCompanyId(demand.getCompanyId());
		companyMap.computeIfPresent(demand.getCompanyId(), (k, v) -> {
			baseRow.setCompanyName(v.getCompanyName());
			return v;
		});

		if (demand.getAccountId() != null) {
			baseRow.setAccountId(demand.getAccountId());
			accountMap.computeIfPresent(demand.getAccountId(), (k, v) -> {
				baseRow.setAccountName(v.getAccountName());
				return v;
			});
		}

		baseRow.setProjectId(null);
		baseRow.setProjectName(demand.getProjectName());

		baseRow.setSummaryTotalRequests(summary.getOrDefault("totalRequests", 0));
		baseRow.setSummaryOpen(summary.getOrDefault("open", 0));
		baseRow.setSummaryInterviewing(summary.getOrDefault("interviewing", 0));
		baseRow.setSummaryAllocated(summary.getOrDefault("allocated", 0));
		baseRow.setSummaryRejected(summary.getOrDefault("rejected", 0));

		long pendingDays = (openAt != null) ? ChronoUnit.DAYS.between(openAt.toLocalDate(), LocalDate.now()) : 0;
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
					interviewRow.setCandidateEmployeeId(interview.getEmployeeId());
					Employee candidate = employeeMap.get(interview.getEmployeeId());
					if (candidate != null) {
						interviewRow.setCandidateName(candidate.getFirstName() + " " + candidate.getLastName());
						interviewRow.setCandidateEmail(candidate.getEmail());
						interviewRow.setCandidatePhoneNumber(candidate.getPhoneNumber());
						EmployeeDocument resume = resumeMap.get(candidate.getEmployeeId());
						if (resume != null) {
							interviewRow.setCandidateResumeStatus(resume.getResumeShareStatus());
						}
					}
				}

				Allocation alloc = allocByReqMap.get(req.getRequestId());
				if (alloc != null && Objects.equals(alloc.getEmployeeId(), interview.getEmployeeId())) {
					interviewRow.setAllocationId(alloc.getAllocationId());
					Employee allocEmp = employeeMap.get(alloc.getEmployeeId());
					interviewRow.setAllocationEmployeeName(
							allocEmp != null ? allocEmp.getFirstName() + " " + allocEmp.getLastName() : null);
					interviewRow.setAllocationStartDate(alloc.getStartDate());
					interviewRow.setAllocationEndDate(alloc.getEndDate());
					interviewRow.setAllocationProjectRole(alloc.getProjectRole());
					interviewRow.setAllocationIsBillable(alloc.getIsBillable());
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
								if (emp != null) {
									levelRow.setInterviewerName(emp.getFirstName() + " " + emp.getLastName());
								}
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
    private List<LevelProgressDto> readProgress(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return om.readValue(json, new TypeReference<List<LevelProgressDto>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
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
        copy.setDemandOpenDt(base.getDemandOpenDt());
        copy.setFulfilmentDt(base.getFulfilmentDt());
        copy.setPriority(base.getPriority());
        copy.setRoleDuration(base.getRoleDuration());

        copy.setSummaryTotalRequests(base.getSummaryTotalRequests());
        copy.setSummaryOpen(base.getSummaryOpen());
        copy.setSummaryInterviewing(base.getSummaryInterviewing());
        copy.setSummaryAllocated(base.getSummaryAllocated());
        copy.setSummaryRejected(base.getSummaryRejected());
        copy.setSummaryPendingDays(base.getSummaryPendingDays());

        copy.setRequestId(base.getRequestId());
        copy.setRequestStatus(base.getRequestStatus());

        copy.setInterviewId(base.getInterviewId());
        copy.setInterviewOverallStatus(base.getInterviewOverallStatus());

        copy.setCandidateEmployeeId(base.getCandidateEmployeeId());
        copy.setCandidateName(base.getCandidateName());
        copy.setCandidateEmail(base.getCandidateEmail());
        copy.setCandidatePhoneNumber(base.getCandidatePhoneNumber());
        copy.setCandidateResumeStatus(base.getCandidateResumeStatus());

        copy.setAllocationId(base.getAllocationId());
        copy.setAllocationEmployeeName(base.getAllocationEmployeeName());
        copy.setAllocationStartDate(base.getAllocationStartDate());
        copy.setAllocationEndDate(base.getAllocationEndDate());
        copy.setAllocationProjectRole(base.getAllocationProjectRole());
        copy.setAllocationIsBillable(base.getAllocationIsBillable());

        copy.setInterviewLevel(base.getInterviewLevel());
        copy.setInterviewLevelStatus(base.getInterviewLevelStatus());
        copy.setInterviewNotes(base.getInterviewNotes());
        copy.setInterviewCompletedAt(base.getInterviewCompletedAt());
        copy.setInterviewerUserId(base.getInterviewerUserId());
        copy.setInterviewerName(base.getInterviewerName());
        copy.setInterviewerEmail(base.getInterviewerEmail());

        return copy;
    }

    private LocalDate parseFlexibleDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
            }
        }
        return null;
    }

}
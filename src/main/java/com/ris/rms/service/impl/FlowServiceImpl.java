package com.ris.rms.service.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.dto.AllocationDto;
import com.ris.rms.dto.CandidateDto;
import com.ris.rms.dto.EmployeeDto;
import com.ris.rms.dto.GroupRequestView;
import com.ris.rms.dto.InterviewDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.dto.ResourceRequestDto;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.CandidateRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.CandidateService;
import com.ris.rms.service.EmployeeService;
import com.ris.rms.service.FlowService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlowServiceImpl implements FlowService {

	private final EmployeeService employeeService;
	private final CandidateService candidateService;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final CandidateRepository candidateRepo;

	@PersistenceContext
	private EntityManager em;

	private final ObjectMapper om = new ObjectMapper();
	private static final DateTimeFormatter SCHED_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm");
	private static final LocalDate MIN_DAY = LocalDate.of(1, 1, 1);
	private static final LocalDate MAX_DAY = LocalDate.of(9999, 12, 31);

	private static Date sql(LocalDate d) {
		return Date.valueOf(d);
	}

	@Override
	public Map<String, Object> getEmployeeFlow(Long employeeId, int page, int size, LocalDate fromDate,
			LocalDate toDate) {
		if (employeeId == null)
			throw new IllegalArgumentException("employeeId is required");
		EmployeeDto profile = employeeService.getById(employeeId);
		if (profile == null)
			return pageWrap(List.of(), 0, 0, 0);

		Map<Long, List<AllocationDto>> allocations = fetchAllocationsForEmployees(List.of(employeeId), MIN_DAY,
				MAX_DAY);
		Map<Long, List<InterviewDto>> interviews = fetchInterviewsForEmployees(List.of(employeeId), MIN_DAY, MAX_DAY);

		LinkSet links = collectProjectAndRequestLinks(employeeId, allocations.getOrDefault(employeeId, List.of()),
				interviews.getOrDefault(employeeId, List.of()));

		Map<Long, List<ResourceRequestDto>> requestsByProject = links.projectIds.isEmpty() ? Map.of()
				: fetchRequestsForProjects(new ArrayList<>(links.projectIds), MIN_DAY, MAX_DAY);
		Map<Long, ResourceRequestDto> requestsById = links.requestIds.isEmpty() ? Map.of()
				: fetchRequestsByIds(new ArrayList<>(links.requestIds));

		List<ResourceRequestDto> mappedReqs = mapRequestsToEmployeeWithLinks(employeeId, links, requestsByProject,
				requestsById);
		List<InterviewDto> ints = interviews.getOrDefault(employeeId, List.of());

		Map<String, Object> one = buildRow(profile, allocations.getOrDefault(employeeId, List.of()), mappedReqs,
				normalizeInterviewLevels(ints));

		return pageWrap(List.of(one), 0, 1, 1);
	}

	@Override
	public Map<String, Object> listEmployeeFlows(int page, int size, Long companyId, String q, String status,
			Long departmentId, LocalDate fromDate, LocalDate toDate) {
		List<EmployeeDto> employees = employeeService.list(companyId, q, status, departmentId, page, size);
		if (employees.isEmpty())
			return pageWrap(List.of(), page, size, 0);

		LocalDate f = nonNull(fromDate, MIN_DAY);
		LocalDate t = nonNull(toDate, MAX_DAY);

		List<EmployeeDto> inWindow = new ArrayList<>();
		for (EmployeeDto e : employees) {
			LocalDate j = e.getJoiningDate();
			if (j == null || (!j.isBefore(f) && !j.isAfter(t))) {
				inWindow.add(e);
			}
		}
		if (inWindow.isEmpty())
			return pageWrap(List.of(), page, size, 0);

		List<Long> empIds = inWindow.stream().map(EmployeeDto::getEmployeeId).filter(Objects::nonNull).toList();

		Map<Long, List<AllocationDto>> allocationsByEmp = fetchAllocationsForEmployees(empIds, MIN_DAY, MAX_DAY);
		Map<Long, List<InterviewDto>> interviewsByEmp = fetchInterviewsForEmployees(empIds, MIN_DAY, MAX_DAY);

		Map<Long, LinkSet> linksByEmp = new LinkedHashMap<>();
		Set<Long> allProjectIds = new LinkedHashSet<>();
		Set<Long> allRequestIds = new LinkedHashSet<>();

		for (EmployeeDto e : inWindow) {
			Long empId = e.getEmployeeId();
			LinkSet ls = collectProjectAndRequestLinks(empId, allocationsByEmp.getOrDefault(empId, List.of()),
					interviewsByEmp.getOrDefault(empId, List.of()));
			linksByEmp.put(empId, ls);
			allProjectIds.addAll(ls.projectIds);
			allRequestIds.addAll(ls.requestIds);
		}

		Map<Long, List<ResourceRequestDto>> requestsByProject = allProjectIds.isEmpty() ? Map.of()
				: fetchRequestsForProjects(new ArrayList<>(allProjectIds), MIN_DAY, MAX_DAY);
		Map<Long, ResourceRequestDto> requestsById = allRequestIds.isEmpty() ? Map.of()
				: fetchRequestsByIds(new ArrayList<>(allRequestIds));

		List<Map<String, Object>> items = new ArrayList<>(inWindow.size());
		for (EmployeeDto e : inWindow) {
			Long empId = e.getEmployeeId();
			List<AllocationDto> als = allocationsByEmp.getOrDefault(empId, List.of());
			List<InterviewDto> ints = normalizeInterviewLevels(interviewsByEmp.getOrDefault(empId, List.of()));
			LinkSet ls = linksByEmp.get(empId);
			List<ResourceRequestDto> reqs = mapRequestsToEmployeeWithLinks(empId, ls, requestsByProject, requestsById);
			items.add(buildRow(e, als, reqs, ints));
		}

		return pageWrap(items, page, size, items.size());
	}

	@Override
	public Map<String, Object> getCandidateFlow(Long candidateId, int page, int size, LocalDate fromDate,
			LocalDate toDate) {
		if (candidateId == null)
			throw new IllegalArgumentException("candidateId is required");

		CandidateDto profile = candidateService.getById(candidateId);

		Map<Long, List<AllocationDto>> allocations = fetchAllocationsForCandidates(List.of(candidateId), MIN_DAY,
				MAX_DAY);
		Map<Long, List<InterviewDto>> interviews = fetchInterviewsForCandidates(List.of(candidateId), MIN_DAY, MAX_DAY);

		LinkSet links = collectProjectAndRequestLinks(candidateId, allocations.getOrDefault(candidateId, List.of()),
				interviews.getOrDefault(candidateId, List.of()));

		Map<Long, List<ResourceRequestDto>> requestsByProject = links.projectIds.isEmpty() ? Map.of()
				: fetchRequestsForProjects(new ArrayList<>(links.projectIds), MIN_DAY, MAX_DAY);
		Map<Long, ResourceRequestDto> requestsById = links.requestIds.isEmpty() ? Map.of()
				: fetchRequestsByIds(new ArrayList<>(links.requestIds));

		List<ResourceRequestDto> mappedReqs = mapRequestsToEmployeeWithLinks(candidateId, links, requestsByProject,
				requestsById);
		List<InterviewDto> ints = interviews.getOrDefault(candidateId, List.of());

		Map<String, Object> one = buildCandidateRow(profile, allocations.getOrDefault(candidateId, List.of()),
				mappedReqs, normalizeInterviewLevels(ints));

		return pageWrap(List.of(one), 0, 1, 1);
	}

	@Override
	public Map<String, Object> listCandidateFlows(int page, int size, Long companyId, String q, String status,
			String sourceType, LocalDate fromDate, LocalDate toDate) {

		List<CandidateDto> candidates = candidateService.list(companyId, q, status, sourceType, page, size);
		if (candidates.isEmpty())
			return pageWrap(List.of(), page, size, 0);

		LocalDate f = nonNull(fromDate, MIN_DAY);
		LocalDate t = nonNull(toDate, MAX_DAY);

		List<CandidateDto> inWindow = new ArrayList<>();
		for (CandidateDto c : candidates) {
			LocalDate created = toLocalDate(c.getCreatedAt());
			if (created == null || (!created.isBefore(f) && !created.isAfter(t))) {
				inWindow.add(c);
			}
		}
		if (inWindow.isEmpty())
			return pageWrap(List.of(), page, size, 0);

		List<Long> candIds = inWindow.stream().map(CandidateDto::getCandidateId).filter(Objects::nonNull).toList();

		Map<Long, List<AllocationDto>> allocationsByCand = fetchAllocationsForCandidates(candIds, MIN_DAY, MAX_DAY);
		Map<Long, List<InterviewDto>> interviewsByCand = fetchInterviewsForCandidates(candIds, MIN_DAY, MAX_DAY);

		Map<Long, LinkSet> linksByCand = new LinkedHashMap<>();
		Set<Long> allProjectIds = new LinkedHashSet<>();
		Set<Long> allRequestIds = new LinkedHashSet<>();

		for (CandidateDto c : inWindow) {
			Long cId = c.getCandidateId();
			LinkSet ls = collectProjectAndRequestLinks(cId, allocationsByCand.getOrDefault(cId, List.of()),
					interviewsByCand.getOrDefault(cId, List.of()));
			linksByCand.put(cId, ls);
			allProjectIds.addAll(ls.projectIds);
			allRequestIds.addAll(ls.requestIds);
		}

		Map<Long, List<ResourceRequestDto>> requestsByProject = allProjectIds.isEmpty() ? Map.of()
				: fetchRequestsForProjects(new ArrayList<>(allProjectIds), MIN_DAY, MAX_DAY);
		Map<Long, ResourceRequestDto> requestsById = allRequestIds.isEmpty() ? Map.of()
				: fetchRequestsByIds(new ArrayList<>(allRequestIds));

		List<Map<String, Object>> items = new ArrayList<>(inWindow.size());
		for (CandidateDto c : inWindow) {
			Long cId = c.getCandidateId();
			List<AllocationDto> als = allocationsByCand.getOrDefault(cId, List.of());
			List<InterviewDto> ints = normalizeInterviewLevels(interviewsByCand.getOrDefault(cId, List.of()));
			LinkSet ls = linksByCand.get(cId);
			List<ResourceRequestDto> reqs = mapRequestsToEmployeeWithLinks(cId, ls, requestsByProject, requestsById);
			items.add(buildCandidateRow(c, als, reqs, ints));
		}

		return pageWrap(items, page, size, items.size());
	}

	private static class LinkSet {
		final Set<Long> projectIds;
		final Set<Long> requestIds;

		LinkSet(Set<Long> pids, Set<Long> rids) {
			this.projectIds = pids;
			this.requestIds = rids;
		}
	}

	private LinkSet collectProjectAndRequestLinks(Long empId, List<AllocationDto> allocations,
			List<InterviewDto> interviews) {
		Set<Long> pids = new LinkedHashSet<>();
		Set<Long> rids = new LinkedHashSet<>();

		if (allocations != null) {
			for (AllocationDto a : allocations) {
				if (a.getProjectId() != null)
					pids.add(a.getProjectId());
				if (a.getRequestId() != null)
					rids.add(a.getRequestId());
			}
		}
		if (interviews != null) {
			for (InterviewDto i : interviews) {
				if (i.getProjectId() != null)
					pids.add(i.getProjectId());
				if (i.getRequestId() != null)
					rids.add(i.getRequestId());
			}
		}
		return new LinkSet(pids, rids);
	}

	private ResourceRequestDto mapReqRow(Object[] r, int offset) {
		int c = offset;
		Long rid = ((Number) r[c++]).longValue();
		ResourceRequestDto d = new ResourceRequestDto();
		d.setRequestId(rid);
		d.setProjectId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;
		d.setProjectName((String) r[c++]);
		d.setCompanyId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;
		d.setCompanyName((String) r[c++]);
		d.setAccountId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;
		d.setAccountName((String) r[c++]);
		d.setRequesterUserId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;
		d.setRequesterEmail((String) r[c++]);
		d.setRequesterName((String) r[c++]);
		d.setNumberOfResources((Integer) r[c++]);
		d.setExperienceRange((String) r[c++]);
		d.setLocationType((String) r[c++]);
		d.setWorkMode((String) r[c++]);
		d.setLocation((String) r[c++]);
		d.setPriority((String) r[c++]);
		d.setStatus((String) r[c++]);
		d.setSubmittedDate(toLocalDate(r[c++]));
		d.setEstimatedCostTotal((BigDecimal) r[c++]);
		d.setEstimatedCostPerResourceMonth((BigDecimal) r[c++]);
		d.setGroupId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;
		d.setDemandId(r[c] != null ? ((Number) r[c]).longValue() : null);
		c++;

		if (d.getSubmittedDate() != null && "Submitted".equalsIgnoreCase(d.getStatus())) {
			d.setDaysPending(java.time.temporal.ChronoUnit.DAYS.between(d.getSubmittedDate(), LocalDate.now()));
		}

		SkillBucket prim = fetchRequestSkills(d.getRequestId(), "primary");
		SkillBucket sec = fetchRequestSkills(d.getRequestId(), "secondary");
		d.setPrimarySkillIds(prim.ids);
		d.setPrimarySkills(prim.names);
		d.setSecondarySkillIds(sec.ids);
		d.setSecondarySkills(sec.names);

		List<Long> mergedIds = new ArrayList<>(new LinkedHashSet<>(concat(prim.ids, sec.ids)));
		List<String> mergedNames = new ArrayList<>(new LinkedHashSet<>(concat(prim.names, sec.names)));
		d.setSkillIds(mergedIds);
		d.setSkills(mergedNames);
		return d;
	}

	private Map<String, Object> buildRow(EmployeeDto profile, List<AllocationDto> allocations,
			List<ResourceRequestDto> requests, List<InterviewDto> interviews) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("profile", profile);
		row.put("allocations", allocations);
		row.put("resourceRequests", requests);
		row.put("interviews", interviews);
		populateGroups(row, requests);
		return row;
	}

	private Map<String, Object> buildCandidateRow(CandidateDto profile, List<AllocationDto> allocations,
			List<ResourceRequestDto> requests, List<InterviewDto> interviews) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("profile", profile);
		row.put("allocations", allocations);
		row.put("resourceRequests", requests);
		row.put("interviews", interviews);
		populateGroups(row, requests);
		return row;
	}

	private void populateGroups(Map<String, Object> row, List<ResourceRequestDto> requests) {
		Set<Long> groupIds = new LinkedHashSet<>();
		if (requests != null) {
			for (ResourceRequestDto r : requests) {
				if (r.getGroupId() != null)
					groupIds.add(r.getGroupId());
			}
		}
		if (!groupIds.isEmpty()) {
			Map<Long, GroupRequestView> groups = fetchGroupsByIds(groupIds);
			row.put("groupRequests", new ArrayList<>(groups.values()));
		} else {
			row.put("groupRequests", List.of());
		}
	}

	private Map<String, Object> pageWrap(List<?> items, int page, int size, long total) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("items", items);
		m.put("page", page);
		m.put("size", size);
		m.put("totalElements", total);
		m.put("totalPages", size <= 0 ? 0 : (int) Math.ceil(total / (double) size));
		return m;
	}

	@SuppressWarnings("unchecked")
	private Map<Long, List<AllocationDto>> fetchAllocationsForEmployees(List<Long> employeeIds, LocalDate from,
			LocalDate to) {
		if (employeeIds == null || employeeIds.isEmpty())
			return Map.of();
		String sql = """
				SELECT
				    al.employee_id,
				    (SELECT CONCAT(e.first_name,' ',e.last_name)
				       FROM rms.employee e
				      WHERE e.employee_id = al.employee_id) AS employee_name,
				    al.allocation_id,
				    al.project_id, p.project_name,
				    p.account_id, acc.account_name,
				    p.company_id, comp.company_name,
				    al.request_id,
				    al.project_role, al.is_billable, al.start_date, al.end_date, al.status
				FROM rms.allocation al
				LEFT JOIN rms.project p ON p.project_id = al.project_id
				LEFT JOIN rms.account acc ON acc.account_id = p.account_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				WHERE al.employee_id IN (:empIds)
				ORDER BY al.employee_id,
				         COALESCE(al.end_date, DATE '9999-12-31') DESC,
				         al.start_date DESC
				""";
		var q = em.createNativeQuery(sql).setParameter("empIds", employeeIds);
		List<Object[]> rows = q.getResultList();

		Map<Long, List<AllocationDto>> map = new LinkedHashMap<>();
		for (Object[] r : rows) {
			int c = 0;
			Long empId = ((Number) r[c++]).longValue();
			String empName = (String) r[c++];

			AllocationDto d = new AllocationDto();
			d.setEmployeeId(empId);
			d.setEmployeeName(empName);
			d.setAllocationId(((Number) r[c++]).longValue());
			d.setProjectId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setProjectName((String) r[c++]);
			d.setAccountId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setAccountName((String) r[c++]);
			d.setCompanyId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setCompanyName((String) r[c++]);
			d.setRequestId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setProjectRole((String) r[c++]);
			d.setIsBillable(toBoolean(r[c++]));
			d.setStartDate(toLocalDate(r[c++]));
			d.setEndDate(toLocalDate(r[c++]));
			d.setStatus((String) r[c++]);

			map.computeIfAbsent(empId, k -> new ArrayList<>()).add(d);
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private Map<Long, List<AllocationDto>> fetchAllocationsForCandidates(List<Long> candIds, LocalDate from,
			LocalDate to) {
		if (candIds == null || candIds.isEmpty())
			return Map.of();
		String sql = """
				SELECT
				    al.candidate_id,
				    (SELECT CONCAT(c.first_name,' ',c.last_name)
				       FROM rms.candidate c
				      WHERE c.candidate_id = al.candidate_id) AS candidate_name,
				    al.allocation_id,
				    al.project_id, p.project_name,
				    p.account_id, acc.account_name,
				    p.company_id, comp.company_name,
				    al.request_id,
				    al.project_role, al.is_billable, al.start_date, al.end_date, al.status
				FROM rms.allocation al
				LEFT JOIN rms.project p ON p.project_id = al.project_id
				LEFT JOIN rms.account acc ON acc.account_id = p.account_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				WHERE al.candidate_id IN (:candIds)
				ORDER BY al.candidate_id,
				         COALESCE(al.end_date, DATE '9999-12-31') DESC,
				         al.start_date DESC
				""";
		var q = em.createNativeQuery(sql).setParameter("candIds", candIds);
		List<Object[]> rows = q.getResultList();

		Map<Long, List<AllocationDto>> map = new LinkedHashMap<>();
		for (Object[] r : rows) {
			int c = 0;
			Long cId = ((Number) r[c++]).longValue();
			String cName = (String) r[c++];

			AllocationDto d = new AllocationDto();
			d.setCandidateId(cId);
			d.setCandidateName(cName);

			d.setAllocationId(((Number) r[c++]).longValue());
			d.setProjectId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setProjectName((String) r[c++]);
			d.setAccountId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setAccountName((String) r[c++]);
			d.setCompanyId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setCompanyName((String) r[c++]);
			d.setRequestId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setProjectRole((String) r[c++]);
			d.setIsBillable(toBoolean(r[c++]));
			d.setStartDate(toLocalDate(r[c++]));
			d.setEndDate(toLocalDate(r[c++]));
			d.setStatus((String) r[c++]);

			map.computeIfAbsent(cId, k -> new ArrayList<>()).add(d);
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private Map<Long, List<ResourceRequestDto>> fetchRequestsForProjects(List<Long> projectIds, LocalDate from,
			LocalDate to) {
		if (projectIds == null || projectIds.isEmpty())
			return Map.of();
		String sql = """
				SELECT rr.project_id,
				       rr.request_id,
				       rr.project_id, p.project_name,
				       p.company_id, comp.company_name,
				       p.account_id, acc.account_name,
				       rr.requester_id, ua.email,
				       (SELECT CONCAT(e.first_name,' ',e.last_name)
				          FROM rms.employee e
				          JOIN rms.user_account u ON u.employee_id = e.employee_id
				         WHERE u.user_id = rr.requester_id) AS requester_name,
				       rr.number_of_resources, rr.experience_range, rr.location_type, rr.work_mode, rr.location,
				       rr.priority, rr.status, rr.submitted_date,
				       rr.estimated_cost_total, rr.estimated_cost_per_resource_month,
				       rr.group_id, rr.demand_id
				FROM rms.resource_request rr
				LEFT JOIN rms.project p ON p.project_id = rr.project_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				LEFT JOIN rms.account acc ON acc.account_id = p.account_id
				LEFT JOIN rms.user_account ua ON ua.user_id = rr.requester_id
				WHERE rr.project_id IN (:pids)
				ORDER BY rr.project_id, rr.submitted_date DESC, rr.request_id DESC
				""";
		var q = em.createNativeQuery(sql).setParameter("pids", projectIds);
		List<Object[]> rows = q.getResultList();
		Map<Long, List<ResourceRequestDto>> byProject = new LinkedHashMap<>();
		for (Object[] r : rows) {
			int c = 0;
			Long pidKey = ((Number) r[c++]).longValue();
			ResourceRequestDto d = mapReqRow(r, c);
			byProject.computeIfAbsent(pidKey, k -> new ArrayList<>()).add(d);
		}
		return byProject;
	}

	@SuppressWarnings("unchecked")
	private Map<Long, ResourceRequestDto> fetchRequestsByIds(List<Long> requestIds) {
		if (requestIds == null || requestIds.isEmpty())
			return Map.of();
		String sql = """
				SELECT rr.request_id,
				       rr.project_id, p.project_name,
				       p.company_id, comp.company_name,
				       p.account_id, acc.account_name,
				       rr.requester_id, ua.email,
				       (SELECT CONCAT(e.first_name,' ',e.last_name)
				          FROM rms.employee e
				          JOIN rms.user_account u ON u.employee_id = e.employee_id
				         WHERE u.user_id = rr.requester_id) AS requester_name,
				       rr.number_of_resources, rr.experience_range, rr.location_type, rr.work_mode, rr.location,
				       rr.priority, rr.status, rr.submitted_date,
				       rr.estimated_cost_total, rr.estimated_cost_per_resource_month,
				       rr.group_id, rr.demand_id
				FROM rms.resource_request rr
				LEFT JOIN rms.project p ON p.project_id = rr.project_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				LEFT JOIN rms.account acc ON acc.account_id = p.account_id
				LEFT JOIN rms.user_account ua ON ua.user_id = rr.requester_id
				WHERE rr.request_id IN (:rids)
				""";
		var q = em.createNativeQuery(sql).setParameter("rids", requestIds);
		List<Object[]> rows = q.getResultList();

		Map<Long, ResourceRequestDto> byId = new LinkedHashMap<>();
		for (Object[] r : rows) {
			ResourceRequestDto d = mapReqRow(r, 0);
			byId.put(d.getRequestId(), d);
		}
		return byId;
	}

	private Map<Long, GroupRequestView> fetchGroupsByIds(Set<Long> groupIds) {
		if (groupIds == null || groupIds.isEmpty())
			return Map.of();
		String sql = """
				    SELECT g.group_id, g.status, g.created_at, g.hr_approved_at, g.created_by,
				           COALESCE(CONCAT(e.first_name,' ',e.last_name), ua.email) AS created_by_name,
				           g.title, g.total_requested, g.request_template
				      FROM rms.resource_request_group g
				      LEFT JOIN rms.user_account ua ON ua.user_id = g.created_by
				      LEFT JOIN rms.employee e ON e.employee_id = ua.employee_id
				     WHERE g.group_id IN (:gids) ORDER BY g.group_id
				""";
		var rows = em.createNativeQuery(sql).setParameter("gids", groupIds).getResultList();
		Map<Long, GroupRequestView> out = new LinkedHashMap<>();
		for (Object r0 : rows) {
			Object[] r = (Object[]) r0;
			int c = 0;

			GroupRequestView g = new GroupRequestView();
			Long gid = ((Number) r[c++]).longValue();
			g.setGroupId(gid);
			g.setStatus((String) r[c++]);
			LocalDate createdAt = toLocalDate(r[c++]);
			LocalDate approvedAt = toLocalDate(r[c++]);
			Long createdBy = r[c] != null ? ((Number) r[c]).longValue() : null;
			c++;
			String createdByName = (String) r[c++];
			String title = (String) r[c++];
			Integer totalRequested = (Integer) r[c++];
			Object template = r[c++];

			g.setCreatedAt(createdAt);
			g.setApprovedAt(approvedAt);

			g.setApprovedByUserId(null);
			g.setApprovedByUserName(null);
			g.setNotes(title);

			g.setDaysCompletedToApproved((createdAt != null && approvedAt != null)
					? Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(createdAt, approvedAt))
					: null);
			g.setDaysPendingToApprove((createdAt != null && approvedAt == null)
					? Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDate.now()))
					: null);

			out.put(gid, g);
		}
		return out;
	}

	private static <T> List<T> concat(List<T> a, List<T> b) {
		List<T> out = new ArrayList<>();
		if (a != null)
			out.addAll(a);
		if (b != null)
			out.addAll(b);
		return out;
	}

	private static class SkillBucket {
		final List<Long> ids;
		final List<String> names;

		SkillBucket(List<Long> ids, List<String> names) {
			this.ids = ids;
			this.names = names;
		}
	}

	@SuppressWarnings("unchecked")
	private SkillBucket fetchRequestSkills(Long requestId, String typeLower) {
		String sql = """
				SELECT rs.skill_id, s.skill_name
				FROM rms.resource_request_skill rs
				JOIN rms.skill s ON s.skill_id = rs.skill_id
				WHERE rs.request_id = :rid AND LOWER(COALESCE(rs.skill_type,'primary')) = :t
				ORDER BY s.skill_name
				""";
		List<Object[]> rows = em.createNativeQuery(sql).setParameter("rid", requestId).setParameter("t", typeLower)
				.getResultList();

		List<Long> ids = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (Object[] r : rows) {
			ids.add(((Number) r[0]).longValue());
			names.add((String) r[1]);
		}
		return new SkillBucket(ids, names);
	}

	private List<ResourceRequestDto> mapRequestsToEmployeeWithLinks(Long empId, LinkSet links,
			Map<Long, List<ResourceRequestDto>> requestsByProject, Map<Long, ResourceRequestDto> requestsById) {
		List<ResourceRequestDto> out = new ArrayList<>();
		if (links != null && links.projectIds != null) {
			for (Long pid : links.projectIds) {
				List<ResourceRequestDto> lst = requestsByProject.get(pid);
				if (lst != null)
					out.addAll(lst);
			}
		}
		if (links != null && links.requestIds != null) {
			for (Long rid : links.requestIds) {
				ResourceRequestDto d = requestsById.get(rid);
				if (d != null)
					out.add(d);
			}
		}
		out.sort((a, b) -> {
			var ad = a.getSubmittedDate();
			var bd = b.getSubmittedDate();
			int cmp = (bd == null ? (ad == null ? 0 : 1) : (ad == null ? -1 : bd.compareTo(ad)));
			if (cmp != 0)
				return cmp;
			Long aid = a.getRequestId(), bid = b.getRequestId();
			if (aid == null || bid == null)
				return 0;
			return Long.compare(bid, aid);
		});
		List<ResourceRequestDto> unique = new ArrayList<>();
		Set<Long> seen = new LinkedHashSet<>();
		for (ResourceRequestDto d : out) {
			Long id = d.getRequestId();
			if (id == null || seen.add(id))
				unique.add(d);
		}
		return unique;
	}

	@SuppressWarnings("unchecked")
	private Map<Long, List<InterviewDto>> fetchInterviewsForEmployees(List<Long> employeeIds, LocalDate from,
			LocalDate to) {
		if (employeeIds == null || employeeIds.isEmpty())
			return Map.of();

		String sql = """
				SELECT
				    i.employee_id,
				    i.interview_id, i.request_id, i.employee_id,
				    i.interviewer_id, i.scheduled_at, i.interview_type, i.status,
				    i.notes,
				    COALESCE(p.project_id, rr.project_id) as project_id, 
				    p.project_name, 
				    COALESCE(a.account_id, d.account_id) as account_id, 
				    COALESCE(a.account_name, da.account_name) as account_name,
				    p.company_id, comp.company_name,
				    ua.email AS interviewer_email,
				    fb.feedback_id, fb.rating, fb.recommendation, fb.comments,
				    i.planned_levels::text, i.level_progress::text,
				    i.completed_at,
				    (SELECT CONCAT(e2.first_name,' ',e2.last_name)
				       FROM rms.employee e2
				       JOIN rms.user_account u2 ON u2.employee_id = e2.employee_id
				      WHERE u2.user_id = i.interviewer_id) AS interviewer_name,
				  (SELECT CONCAT(e3.first_name,' ',e3.last_name)
				                FROM rms.employee e3
				               WHERE e3.employee_id = i.employee_id) AS employee_name,
				              (SELECT e4.email
				                 FROM rms.employee e4
				                WHERE e4.employee_id = i.employee_id) AS employee_email
				            FROM rms.interview i
				LEFT JOIN rms.resource_request rr ON rr.request_id = i.request_id
				LEFT JOIN rms.project p ON p.project_id = rr.project_id
				LEFT JOIN rms.account a ON a.account_id = p.account_id
				LEFT JOIN rms.demand d ON d.demandid = rr.demand_id
				LEFT JOIN rms.account da ON da.account_id = d.account_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				LEFT JOIN rms.user_account ua ON ua.user_id = i.interviewer_id
				LEFT JOIN rms.interview_feedback fb ON fb.interview_id = i.interview_id
				WHERE i.employee_id IN (:empIds)
				ORDER BY i.employee_id, i.scheduled_at DESC, i.interview_id DESC
				""";
		var q = em.createNativeQuery(sql).setParameter("empIds", employeeIds);
		return mapInterviews(q.getResultList(), true);
	}

	@SuppressWarnings("unchecked")
	private Map<Long, List<InterviewDto>> fetchInterviewsForCandidates(List<Long> candIds, LocalDate from,
			LocalDate to) {
		if (candIds == null || candIds.isEmpty())
			return Map.of();
		String sql = """
				SELECT
				    i.candidate_id,
				    i.interview_id, i.request_id, i.candidate_id,
				    i.interviewer_id, i.scheduled_at, i.interview_type, i.status,
				    i.notes,
				    COALESCE(p.project_id, rr.project_id) as project_id, 
				    p.project_name, 
				    COALESCE(a.account_id, d.account_id) as account_id, 
				    COALESCE(a.account_name, da.account_name) as account_name,
				    p.company_id, comp.company_name,
				    ua.email AS interviewer_email,
				    fb.feedback_id, fb.rating, fb.recommendation, fb.comments,
				    i.planned_levels::text, i.level_progress::text,
				    i.completed_at,
				    (SELECT CONCAT(e2.first_name,' ',e2.last_name)
				       FROM rms.employee e2
				       JOIN rms.user_account u2 ON u2.employee_id = e2.employee_id
				      WHERE u2.user_id = i.interviewer_id) AS interviewer_name,
				  (SELECT CONCAT(c.first_name,' ',c.last_name)
				    FROM rms.candidate c
				   WHERE c.candidate_id = i.candidate_id) AS candidate_name,
				  (SELECT c.email
				     FROM rms.candidate c
				    WHERE c.candidate_id = i.candidate_id) AS candidate_email
				FROM rms.interview i
				LEFT JOIN rms.resource_request rr ON rr.request_id = i.request_id
				LEFT JOIN rms.project p ON p.project_id = rr.project_id
				LEFT JOIN rms.account a ON a.account_id = p.account_id
				LEFT JOIN rms.demand d ON d.demandid = rr.demand_id
				LEFT JOIN rms.account da ON da.account_id = d.account_id
				LEFT JOIN rms.company comp ON comp.company_id = p.company_id
				LEFT JOIN rms.user_account ua ON ua.user_id = i.interviewer_id
				LEFT JOIN rms.interview_feedback fb ON fb.interview_id = i.interview_id
				WHERE i.candidate_id IN (:candIds)
				ORDER BY i.candidate_id, i.scheduled_at DESC, i.interview_id DESC
				""";
		var q = em.createNativeQuery(sql).setParameter("candIds", candIds);
		return mapInterviews(q.getResultList(), false);
	}

	private Map<Long, List<InterviewDto>> mapInterviews(List<Object[]> rows, boolean isEmployee) {
		Map<Long, List<InterviewDto>> map = new LinkedHashMap<>();
		for (Object[] r : rows) {
			int c = 0;
			Long key = ((Number) r[c++]).longValue();
			InterviewDto d = new InterviewDto();
			d.setInterviewId(((Number) r[c++]).longValue());
			d.setRequestId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			Long pId = r[c] != null ? ((Number) r[c]).longValue() : null;
			c++;
			if (isEmployee)
				d.setEmployeeId(pId);
			else
				d.setCandidateId(pId);

			d.setInterviewerUserId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			OffsetDateTime odt = toOdt(r[c++]);
			if (odt != null)
				d.setScheduledAtText(SCHED_FMT.format(odt));

			d.setInterviewType((String) r[c++]);
			d.setStatus((String) r[c++]);
			d.setFeedback((String) r[c++]);
			d.setProjectId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setProjectName((String) r[c++]);
			d.setAccountId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setAccountName((String) r[c++]);
			d.setCompanyId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setCompanyName((String) r[c++]);
			d.setInterviewerEmail((String) r[c++]);
			d.setFeedbackId(r[c] != null ? ((Number) r[c]).longValue() : null);
			c++;
			d.setRating(r[c] != null ? ((Number) r[c]).intValue() : null);
			c++;
			d.setRecommendation((String) r[c++]);
			d.setFeedbackComments((String) r[c++]);
			String planned = (String) r[c++];
			String progress = (String) r[c++];

			try {
				d.setInterviewLevels((planned != null && !planned.isBlank())
						? om.readValue(planned, new TypeReference<List<String>>() {
						})
						: List.of());
			} catch (Exception ex) {
				d.setInterviewLevels(List.of());
			}

			try {
				if (progress != null && !progress.isBlank()) {
					List<Map<String, Object>> raw = om.readValue(progress, new TypeReference<>() {
					});
					List<LevelProgressDto> lp = new ArrayList<>();
					for (Map<String, Object> m : raw) {
						String lvl = m.get("level") instanceof String s ? s : null;
						String st = m.get("status") instanceof String s ? s : null;
						if (lvl != null && "__META__".equalsIgnoreCase(lvl))
							continue;
						if (isTrivialCreatedRow(m))
							continue;

						LevelProgressDto x = new LevelProgressDto();
						x.setLevel(lvl);
						x.setStatus(st);

						if (m.get("interviewerUserId") instanceof Number n)
							x.setInterviewerUserId(n.longValue());
						if (m.get("interviewerName") instanceof String s)
							x.setInterviewerName(s);
						if (m.get("interviewerEmail") instanceof String s)
							x.setInterviewerEmail(s);
						if (x.getInterviewerUserId() != null
								&& (x.getInterviewerName() == null || x.getInterviewerEmail() == null)) {

							UserAccount ua = userAccountRepo.findById(x.getInterviewerUserId()).orElse(null);
							if (ua != null) {
								if (x.getInterviewerName() == null) {
									x.setInterviewerName(resolveInterviewerDisplayName(ua));
								}
								if (x.getInterviewerEmail() == null) {
									x.setInterviewerEmail(ua.getEmail());
								}
							}
						}
						OffsetDateTime sched = parseAnyOdt(m.get("scheduledAt"));
						if (sched != null) {
							try {
								x.getClass().getMethod("setScheduledAtText", String.class);
								x.setScheduledAtText(SCHED_FMT.format(sched));
							} catch (NoSuchMethodException ignore) {
								try {
									x.getClass().getMethod("setScheduledAt", String.class);
									x.setScheduledAtText(SCHED_FMT.format(sched));
								} catch (NoSuchMethodException ignored2) {
								}
							}
						}

						OffsetDateTime comp = parseAnyOdt(m.get("completedAt"));
						if (comp != null)
							x.setCompletedAtText(SCHED_FMT.format(comp));

						if (m.get("rating") instanceof Number n)
							x.setRating(n.intValue());
						if (m.get("recommendation") instanceof String s)
							x.setRecommendation(s);
						if (m.get("comments") instanceof String s)
							x.setComments(s);
						if (m.get("interviewNotes") instanceof String s)
							x.setFeedback(s);
						if (m.get("feedbackComments") instanceof String s)
							x.setFeedback(s);

						lp.add(x);
					}
					d.setLevelProgress(lp);
				} else {
					d.setLevelProgress(List.of());
				}
			} catch (Exception ex) {
				d.setLevelProgress(List.of());
			}

			OffsetDateTime completedAt = toOdt(r[c++]);
			d.setInterviewerName((String) r[c++]);
			if (isEmployee) {
				d.setEmployeeName((String) r[c++]);
				d.setEmployeeEmail((String) r[c++]);
			} else {
				d.setCandidateName((String) r[c++]);
				d.setCandidateEmail((String) r[c++]);
			}

			OffsetDateTime scheduledAt = null;
			try {
				if (d.getScheduledAtText() != null)
					scheduledAt = parseAnyOdt(d.getScheduledAtText());
			} catch (Exception e) {
			}
			if (scheduledAt != null) {
				if (completedAt != null) {
					d.setDaysToComplete(ChronoUnit.DAYS.between(scheduledAt.toLocalDate(), completedAt.toLocalDate()));
				} else if ("Scheduled".equalsIgnoreCase(d.getStatus())) {
					long days = ChronoUnit.DAYS.between(scheduledAt.toLocalDate(), LocalDate.now());
					d.setDaysPending(days > 0 ? days : 0);
				}
			}
			map.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
		}
		return map;
	}

	private static List<InterviewDto> normalizeInterviewLevels(List<InterviewDto> list) {
		if (list == null || list.isEmpty())
			return list;
		for (InterviewDto i : list) {
			List<LevelProgressDto> lp = i.getLevelProgress();
			if (lp == null)
				continue;
			List<LevelProgressDto> clean = new ArrayList<>();
			for (LevelProgressDto x : lp) {
				String lvl = x.getLevel();
				String st = x.getStatus();
				boolean meta = lvl != null && "__META__".equalsIgnoreCase(lvl);
				boolean trivial = st != null && "created".equalsIgnoreCase(st) && x.getInterviewerUserId() == null
						&& x.getInterviewerName() == null && x.getInterviewerEmail() == null && x.getRating() == null
						&& x.getRecommendation() == null && x.getComments() == null && x.getFeedback() == null
						&& x.getFeedback() == null && x.getScheduledAtText() == null && x.getCompletedAtText() == null;
				if (!meta && !trivial)
					clean.add(x);
			}
			i.setLevelProgress(clean);
		}
		return list;
	}

	private static boolean isTrivialCreatedRow(Map<String, Object> m) {
		String st = m.get("status") instanceof String s ? s : null;
		if (st == null || !"created".equalsIgnoreCase(st))
			return false;
		boolean noSchedule = m.get("scheduledAt") == null && m.get("completedAt") == null;
		boolean noInterviewer = m.get("interviewerUserId") == null && m.get("interviewerName") == null
				&& m.get("interviewerEmail") == null;
		boolean noEval = m.get("rating") == null && m.get("recommendation") == null && m.get("comments") == null
				&& m.get("interviewNotes") == null && m.get("feedbackComments") == null;
		return noSchedule && noInterviewer && noEval;
	}

	private static OffsetDateTime parseAnyOdt(Object v) {
		try {
			if (v == null)
				return null;
			if (v instanceof OffsetDateTime odt)
				return odt;
			if (v instanceof Instant inst)
				return inst.atOffset(ZoneOffset.UTC);
			if (v instanceof LocalDateTime ldt)
				return ldt.atOffset(ZoneOffset.UTC);
			if (v instanceof Timestamp ts)
				return ts.toInstant().atOffset(ZoneOffset.UTC);
			if (v instanceof ZonedDateTime zdt)
				return zdt.toOffsetDateTime();
			if (v instanceof Number n)
				return Instant.ofEpochMilli(n.longValue()).atOffset(ZoneOffset.UTC);
			if (v instanceof String s) {
				String s2 = s.trim();
				if (s2.isEmpty())
					return null;
				try {
					return OffsetDateTime.parse(s2);
				} catch (Exception isoFail) {
					if (s2.matches("\\d{10,13}")) {
						long ms = (s2.length() == 13 ? Long.parseLong(s2) : Long.parseLong(s2) * 1000L);
						return Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC);
					}
					try {
						LocalDateTime ldt = LocalDateTime.parse(s2, DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm"));
						return ldt.atOffset(ZoneOffset.UTC);
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static OffsetDateTime toOdt(Object v) {
		if (v == null)
			return null;
		if (v instanceof OffsetDateTime odt)
			return odt;
		if (v instanceof Instant inst)
			return inst.atOffset(ZoneOffset.UTC);
		if (v instanceof LocalDateTime ldt)
			return ldt.atOffset(ZoneOffset.UTC);
		if (v instanceof Timestamp ts)
			return ts.toInstant().atOffset(ZoneOffset.UTC);
		if (v instanceof ZonedDateTime zdt)
			return zdt.toOffsetDateTime();
		return null;
	}

	private static LocalDate toLocalDate(Object v) {
		if (v == null)
			return null;
		if (v instanceof LocalDate d)
			return d;
		if (v instanceof OffsetDateTime odt)
			return odt.toLocalDate();
		if (v instanceof Date d)
			return d.toLocalDate();
		if (v instanceof LocalDateTime ldt)
			return ldt.toLocalDate();

		if (v instanceof Timestamp ts)
			return ts.toLocalDateTime().toLocalDate();
		if (v instanceof Instant inst)
			return inst.atZone(ZoneOffset.UTC).toLocalDate();
		if (v instanceof ZonedDateTime zdt)
			return zdt.toLocalDate();

		return null;
	}

	private static Boolean toBoolean(Object v) {
		if (v == null)
			return null;
		if (v instanceof Boolean b)
			return b;
		if (v instanceof Number n)
			return n.intValue() != 0;
		if (v instanceof String s)
			return "true".equalsIgnoreCase(s) || "1".equals(s);
		return null;
	}

	private static <T> T nonNull(T v, T fallback) {
		return v != null ? v : fallback;
	}

	private String resolveInterviewerDisplayName(UserAccount ua) {
		if (ua == null)
			return null;
		if (ua.getEmployeeId() != null) {
			var e = employeeRepo.findById(ua.getEmployeeId()).orElse(null);
			if (e != null)
				return (e.getFirstName() + " " + e.getLastName()).trim();
		}
		if (ua.getEmail() != null && !ua.getEmail().isBlank())
			return ua.getEmail();
		return null;
	}
}
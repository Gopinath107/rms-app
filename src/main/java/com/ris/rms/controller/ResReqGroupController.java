package com.ris.rms.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.dto.BulkCreateResReqDto;
import com.ris.rms.dto.BulkCreateResponseDto;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.dto.ResourceRequestGroupDto;
import com.ris.rms.service.ResReqGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resource-requests")
public class ResReqGroupController {

	private final ResReqGroupService service;

	@PostMapping("/bulk-create")
	public ResponseEntity<Map<String, Object>> bulkCreate(@RequestBody BulkCreateResReqDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			BulkCreateResponseDto result = service.bulkCreate(dto);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);

			Long groupId = result != null && result.getGroup() != null ? result.getGroup().getGroupId() : null;
			if (groupId != null) {
				service.saveResponseIntoTemplate(groupId, resp);
			}

			return ResponseEntity.status(HttpStatus.CREATED).body(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@GetMapping("/groups/list")
	public ResponseEntity<Map<String, Object>> listGroups(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) Long projectId, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<ResourceRequestGroupDto> list = service.list(companyId, projectId, page, size);
			resp.put("result", list);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@GetMapping("/groups/get")
	public ResponseEntity<Map<String, Object>> getGroup(@RequestParam Long id) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			ResourceRequestGroupDto one = service.getById(id);
			resp.put("result", one);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@GetMapping("/GroupFlowList")
	public ResponseEntity<Map<String, Object>> getGroupFlowList(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) Long accountId, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long groupId, @RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

			Page<GroupFlowDto> resultPage = service.getGroupFlowList(companyId, accountId, projectId, groupId, fromDate,
					toDate, pageable);

			List<Map<String, Object>> nested = transformToNested(resultPage.getContent());

			resp.put("result", nested);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	private List<Map<String, Object>> transformToNested(List<GroupFlowDto> rows) {
		Map<Long, List<GroupFlowDto>> byGroup = rows.stream()
				.collect(Collectors.groupingBy(GroupFlowDto::getGroupId, LinkedHashMap::new, Collectors.toList()));

		List<Map<String, Object>> out = new ArrayList<>();

		for (Map.Entry<Long, List<GroupFlowDto>> ge : byGroup.entrySet()) {
			List<GroupFlowDto> groupRows = ge.getValue();
			GroupFlowDto g0 = groupRows.stream().findFirst().orElse(null);
			if (g0 == null)
				continue;

			Map<String, Object> groupInfo = new LinkedHashMap<>();
			groupInfo.put("groupId", g0.getGroupId());
			groupInfo.put("title", g0.getGroupTitle());
			groupInfo.put("createdAt", g0.getGroupCreatedAt() == null ? null : g0.getGroupCreatedAt().toString());
			groupInfo.put("totalRequested", g0.getGroupTotalRequested());
			groupInfo.put("status", g0.getGroupStatus());
			groupInfo.put("creatorUserId", g0.getGroupCreatorUserId());
			groupInfo.put("creatorName", g0.getGroupCreatorName());
			groupInfo.put("creatorEmail", g0.getGroupCreatorEmail());

			Map<String, Object> contextInfo = new LinkedHashMap<>();
			contextInfo.put("companyId", g0.getCompanyId());
			contextInfo.put("companyName", g0.getCompanyName());
			contextInfo.put("projectId", g0.getProjectId());
			contextInfo.put("projectName", g0.getProjectName());
			contextInfo.put("accountId", g0.getAccountId());
			contextInfo.put("accountName", g0.getAccountName());

			Map<String, Object> statusSummary = new LinkedHashMap<>();
			statusSummary.put("totalRequests", nvl(g0.getSummaryTotalRequests(), 0));
			statusSummary.put("open", nvl(g0.getSummaryOpen(), 0));
			statusSummary.put("interviewing", nvl(g0.getSummaryInterviewing(), 0));
			statusSummary.put("selected", nvl(g0.getSummarySelected(), 0));
			statusSummary.put("allocated", nvl(g0.getSummaryAllocated(), 0));
			statusSummary.put("onboarded", nvl(g0.getSummaryOnboarded(), 0)); 
			statusSummary.put("rejected", nvl(g0.getSummaryRejected(), 0));
			statusSummary.put("totalInterviews", nvl(g0.getSummaryTotalInterviews(), 0));
			statusSummary.put("pendingDays", nvl(g0.getSummaryPendingDays(), 0L));

			Map<Long, List<GroupFlowDto>> byRequest = groupRows.stream().filter(r -> r.getRequestId() != null).collect(
					Collectors.groupingBy(GroupFlowDto::getRequestId, LinkedHashMap::new, Collectors.toList()));

			List<Map<String, Object>> childRequestDetails = new ArrayList<>();

			for (Map.Entry<Long, List<GroupFlowDto>> re : byRequest.entrySet()) {
				Long reqId = re.getKey();
				List<GroupFlowDto> reqRows = re.getValue();
				GroupFlowDto r0 = reqRows.get(0);

				Map<String, Object> reqObj = new LinkedHashMap<>();
				reqObj.put("requestId", reqId);
				reqObj.put("requestStatus", r0.getRequestStatus());

				Map<Long, List<GroupFlowDto>> byInterview = reqRows.stream().filter(r -> r.getInterviewId() != null)
						.collect(Collectors.groupingBy(GroupFlowDto::getInterviewId, LinkedHashMap::new,
								Collectors.toList()));

				List<Map<String, Object>> pipeline = new ArrayList<>();

				for (Map.Entry<Long, List<GroupFlowDto>> ie : byInterview.entrySet()) {
					Long interviewId = ie.getKey();
					List<GroupFlowDto> ivRows = ie.getValue();
					GroupFlowDto i0 = ivRows.get(0);

					Map<String, Object> interviewObj = new LinkedHashMap<>();
					interviewObj.put("interviewId", interviewId);

					Map<String, Object> candidateInfo = new LinkedHashMap<>();
					candidateInfo.put("employeeId", i0.getCandidateEmployeeId());
					candidateInfo.put("name", i0.getCandidateName());
					candidateInfo.put("email", i0.getCandidateEmail());
					candidateInfo.put("phoneNumber", i0.getCandidatePhoneNumber());
					candidateInfo.put("resumeStatus", i0.getCandidateResumeStatus());
					interviewObj.put("candidateInfo", candidateInfo);

					interviewObj.put("interviewOverallStatus", i0.getInterviewOverallStatus());

					Map<String, Object> allocation = null;
					if (i0.getAllocationId() != null) {
						allocation = new LinkedHashMap<>();
						allocation.put("allocationId", i0.getAllocationId());
						allocation.put("employeeName", i0.getAllocationEmployeeName());
						allocation.put("startDate",
								i0.getAllocationStartDate() == null ? null : i0.getAllocationStartDate().toString());
						allocation.put("endDate",
								i0.getAllocationEndDate() == null ? null : i0.getAllocationEndDate().toString());
						allocation.put("projectRole", i0.getAllocationProjectRole());
						allocation.put("isBillable", i0.getAllocationIsBillable());
					}
					interviewObj.put("allocation", allocation);

					List<Map<String, Object>> interviewLevels = ivRows.stream()
							.filter(x -> x.getInterviewLevel() != null).map(x -> {
								Map<String, Object> lvl = new LinkedHashMap<>();
								lvl.put("level", x.getInterviewLevel());
								lvl.put("status", x.getInterviewLevelStatus());
								lvl.put("interviewerName", x.getInterviewerName());
								lvl.put("notes", x.getInterviewNotes());
								lvl.put("completedAt", x.getInterviewCompletedAt() == null ? null
										: x.getInterviewCompletedAt().toString());
								return lvl;
							}).collect(Collectors.toList());

					interviewObj.put("interviewLevels", interviewLevels);
					pipeline.add(interviewObj);
				}

				reqObj.put("pipeline", pipeline);
				childRequestDetails.add(reqObj);
			}

			Map<String, Object> groupBlock = new LinkedHashMap<>();
			groupBlock.put("groupInfo", groupInfo);
			groupBlock.put("contextInfo", contextInfo);
			groupBlock.put("statusSummary", statusSummary);
			groupBlock.put("childRequestDetails", childRequestDetails);

			out.add(groupBlock);
		}

		return out;
	}

	private static <T> T nvl(T v, T def) {
		return v == null ? def : v;
	}

	private static String cleanMsg(Exception e) {
		String m = e.getMessage();
		return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
	}
}

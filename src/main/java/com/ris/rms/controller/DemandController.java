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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.dto.DemandCreateDto;
import com.ris.rms.dto.DemandReportRequest;
import com.ris.rms.dto.DemandResponseDto;
import com.ris.rms.dto.DetailedResourceReportRequest;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.service.DemandMatchingService;
import com.ris.rms.service.DemandService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demands")
public class DemandController {

	private final DemandService demandService;
	private final DemandMatchingService matchingService;

	@GetMapping("/{id}/matches")
	public ResponseEntity<Map<String, Object>> findMatches(@PathVariable Long id) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Map<String, Object> result = matchingService.findMatchesForDemand(id);

			resp.put("success", true);
			resp.put("result", result);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);

			return ResponseEntity.ok(resp);
		} catch (IllegalArgumentException e) {
			resp.put("success", false);
			resp.put("result", null);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
		} catch (Exception e) {
			resp.put("success", false);
			resp.put("result", null);
			resp.put("errors", List.of("Failed to find matches for demand"));
			resp.put("errorCount", 1);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
		}
	}

	@PostMapping("/generateEmail")
	public ResponseEntity<Map<String, Object>> generateReportEmail(@RequestBody DemandReportRequest request) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			demandService.generateReport(request);

			resp.put("result", "Report generation initiated. Email will be sent to the specified recipients.");
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

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> createDemand(@Valid @RequestBody DemandCreateDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			DemandResponseDto saved = demandService.createDemand(dto);
			resp.put("result", saved);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.status(HttpStatus.CREATED).body(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<Map<String, Object>> updateDemand(@PathVariable Long id, @Valid @RequestBody DemandCreateDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			DemandResponseDto updated = demandService.updateDemand(id, dto);
			resp.put("result", updated);
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

	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> getDemandById(@PathVariable Long id) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			DemandResponseDto demand = demandService.getDemandById(id);
			resp.put("result", demand);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
		}
	}

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> listDemands(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) Long accountId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<DemandResponseDto> list = demandService.listDemands(companyId, accountId, departmentId, status, page,
					size);
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

	@PostMapping("/exportReport")
	public ResponseEntity<byte[]> exportExcelReport(@RequestBody DemandReportRequest request) {
		try {
			byte[] excelBytes = demandService.generateExcelReport(request);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Demand_Report.xlsx")
					.contentType(MediaType
							.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.body(excelBytes);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	@GetMapping("/DemandFlowList")
	public ResponseEntity<Map<String, Object>> getDemandFlowList(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) Long accountId, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) String status, @RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "demandopendt"));

			Page<GroupFlowDto> resultPage = demandService.getDemandFlowList(companyId, accountId, departmentId, status,
					fromDate, toDate, pageable);

			List<Map<String, Object>> nested = transformDemandToNested(resultPage.getContent());

			resp.put("result", nested);
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

	// Inside DemandController class

    private List<Map<String, Object>> transformDemandToNested(List<GroupFlowDto> rows) {
        Map<Long, List<GroupFlowDto>> byGroup = rows.stream()
                .collect(Collectors.groupingBy(GroupFlowDto::getGroupId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> out = new ArrayList<>();

        for (Map.Entry<Long, List<GroupFlowDto>> ge : byGroup.entrySet()) {
            List<GroupFlowDto> groupRows = ge.getValue();
            GroupFlowDto g0 = groupRows.stream().findFirst().orElse(null);
            if (g0 == null) continue;

            // ... (Existing summary logic for anyShared/anyRejected remains same) ...
            boolean anyShared = groupRows.stream().anyMatch(r -> r.getCandidateResumeStatus() != null
                    && "Shared".equalsIgnoreCase(r.getCandidateResumeStatus()));
            boolean anyRejected = groupRows.stream().anyMatch(r -> r.getCandidateResumeStatus() != null
                    && "Rejected".equalsIgnoreCase(r.getCandidateResumeStatus()));
            String resumeShareStatus = anyShared ? "Shared" : (anyRejected ? "Rejected" : "No Resumes");

            // ... (Existing groupInfo, contextInfo, statusSummary maps remain the same) ...
            Map<String, Object> groupInfo = new LinkedHashMap<>();
            groupInfo.put("demandId", g0.getGroupId());
            groupInfo.put("title", g0.getGroupTitle());
            // ... copy rest of groupInfo fields ...
            groupInfo.put("description", g0.getDescription());
            groupInfo.put("createdAt", g0.getGroupCreatedAt() == null ? null : g0.getGroupCreatedAt().toString());
            groupInfo.put("totalRequested", g0.getGroupTotalRequested());
            groupInfo.put("status", g0.getGroupStatus());
            groupInfo.put("creatorUserId", g0.getGroupCreatorUserId());
            groupInfo.put("creatorName", g0.getGroupCreatorName());
            groupInfo.put("creatorEmail", g0.getGroupCreatorEmail());
            groupInfo.put("priority", g0.getPriority());
            groupInfo.put("demandOpenDt", g0.getDemandOpenDt());
            groupInfo.put("fulfilmentDt", g0.getFulfilmentDt());
            groupInfo.put("actualFulfilmentDt", g0.getActualFulfilmentDt());
            groupInfo.put("fulfilledWithinTarget", g0.getFulfilledWithinTarget());
            groupInfo.put("roleDuration", g0.getRoleDuration());

            Map<String, Object> contextInfo = new LinkedHashMap<>();
            contextInfo.put("companyId", g0.getCompanyId());
            contextInfo.put("companyName", g0.getCompanyName());
            contextInfo.put("projectId", g0.getProjectId());
            contextInfo.put("projectName", g0.getProjectName());
            contextInfo.put("accountId", g0.getAccountId());
            contextInfo.put("accountName", g0.getAccountName());

            Map<String, Object> statusSummary = new LinkedHashMap<>();
            statusSummary.put("totalRequests", nvl(g0.getSummaryTotalRequests(), 0));
            // ... copy rest of summary fields ...
            statusSummary.put("open", nvl(g0.getSummaryOpen(), 0));
            statusSummary.put("interviewing", nvl(g0.getSummaryInterviewing(), 0));
            statusSummary.put("selected", nvl(g0.getSummarySelected(), 0));
            statusSummary.put("allocated", nvl(g0.getSummaryAllocated(), 0));
            statusSummary.put("onboarded", nvl(g0.getSummaryOnboarded(), 0));
            statusSummary.put("rejected", nvl(g0.getSummaryRejected(), 0));
            statusSummary.put("totalInterviews", nvl(g0.getSummaryTotalInterviews(), 0));
            statusSummary.put("pendingDays", nvl(g0.getSummaryPendingDays(), 0L));
            statusSummary.put("resumeShareStatus", resumeShareStatus);

            // --- CHANGED SECTION: Child Request Processing ---
            Map<Long, List<GroupFlowDto>> byRequest = groupRows.stream().filter(r -> r.getRequestId() != null)
                    .collect(Collectors.groupingBy(GroupFlowDto::getRequestId, LinkedHashMap::new, Collectors.toList()));

            List<Map<String, Object>> childRequestDetails = new ArrayList<>();

            for (Map.Entry<Long, List<GroupFlowDto>> re : byRequest.entrySet()) {
                Long reqId = re.getKey();
                List<GroupFlowDto> reqRows = re.getValue();
                GroupFlowDto r0 = reqRows.get(0);

                Map<String, Object> reqObj = new LinkedHashMap<>();
                reqObj.put("requestId", reqId);
                reqObj.put("requestStatus", r0.getRequestStatus());

                Map<Long, List<GroupFlowDto>> byInterview = reqRows.stream().filter(r -> r.getInterviewId() != null)
                        .collect(Collectors.groupingBy(GroupFlowDto::getInterviewId, LinkedHashMap::new, Collectors.toList()));

                List<Map<String, Object>> pipeline = new ArrayList<>();

                for (Map.Entry<Long, List<GroupFlowDto>> ie : byInterview.entrySet()) {
                    Long interviewId = ie.getKey();
                    List<GroupFlowDto> ivRows = ie.getValue();
                    GroupFlowDto i0 = ivRows.get(0);

                    Map<String, Object> interviewObj = new LinkedHashMap<>();
                    interviewObj.put("interviewId", interviewId);
                    interviewObj.put("interviewOverallStatus", i0.getInterviewOverallStatus());
                    
                    Map<String, Object> candidateInfo = new LinkedHashMap<>();
                    candidateInfo.put("resourceType", i0.getResourceType()); 
                    candidateInfo.put("candidateId", i0.getCandidateId()); 
                    candidateInfo.put("employeeId", i0.getCandidateEmployeeId());
                    candidateInfo.put("name", i0.getCandidateName());
                    candidateInfo.put("email", i0.getCandidateEmail());
                    candidateInfo.put("phoneNumber", i0.getCandidatePhoneNumber());
                    candidateInfo.put("resumeStatus", i0.getCandidateResumeStatus());
                    candidateInfo.put("designation", i0.getCandidateDesignation());
                    candidateInfo.put("experienceYears", i0.getCandidateExperience());
                    
                    interviewObj.put("candidateInfo", candidateInfo);

                    Map<String, Object> allocation = null;
                    if (i0.getAllocationId() != null) {
                        allocation = new LinkedHashMap<>();
                        allocation.put("allocationId", i0.getAllocationId());
                        allocation.put("employeeName", i0.getAllocationEmployeeName());
                        allocation.put("startDate", i0.getAllocationStartDate() == null ? null : i0.getAllocationStartDate().toString());
                        allocation.put("endDate", i0.getAllocationEndDate() == null ? null : i0.getAllocationEndDate().toString());
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
                                lvl.put("completedAt", x.getInterviewCompletedAt() == null ? null : x.getInterviewCompletedAt().toString());
                                return lvl;
                            }).collect(Collectors.toList());

                    interviewObj.put("interviewLevels", interviewLevels);
                    pipeline.add(interviewObj);
                }

                reqObj.put("pipeline", pipeline);
                childRequestDetails.add(reqObj);
            }

            Map<String, Object> groupBlock = new LinkedHashMap<>();
            groupBlock.put("demandInfo", groupInfo);
            groupBlock.put("contextInfo", contextInfo);
            groupBlock.put("statusSummary", statusSummary);
            groupBlock.put("childRequestDetails", childRequestDetails);

            out.add(groupBlock);
        }
        return out;
    }

	@PostMapping("/exportDetailedReport")
	public ResponseEntity<byte[]> exportDetailedResourceReport(
			@RequestBody DetailedResourceReportRequest request) {
		try {
			byte[] excelBytes = demandService.generateDetailedResourceReport(request);
			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"attachment; filename=Detailed_Resource_Report.xlsx")
					.contentType(MediaType.parseMediaType(
							"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
					.body(excelBytes);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	private static <T> T nvl(T v, T def) {
		return v == null ? def : v;
	}

}
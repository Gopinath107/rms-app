package com.ris.rms.controller;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import com.ris.rms.dto.InterviewDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.service.InterviewService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interviews")
public class InterviewBatchController {

	private final InterviewService service;
	private final ResourceRequestRepository rrRepo;
	private final DemandRepository demandRepo;

	@PostMapping("/batch")
	public ResponseEntity<Map<String, Object>> createBatch(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long requestId = asLong(body.get("requestId"));
			Long employeeId = asLong(body.get("employeeId"));
			Long candidateId = asLong(body.get("candidateId"));
			
			Long createdByUserId = asLong(body.get("createdbyUserId"));
			if (createdByUserId == null) createdByUserId = asLong(body.get("createdByUserId"));
			@SuppressWarnings("unchecked")
			List<String> interviewLevels = (List<String>) body.get("interviewLevels");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> levels = (List<Map<String, Object>>) body.get("levels");
			
			if (requestId == null) throw new IllegalArgumentException("requestId is required");
			
			if (employeeId == null && candidateId == null) throw new IllegalArgumentException("Either employeeId or candidateId is required");
			if (employeeId != null && candidateId != null) throw new IllegalArgumentException("Cannot specify both employeeId and candidateId");
			
			if (interviewLevels == null || interviewLevels.isEmpty()) throw new IllegalArgumentException("interviewLevels is required");
			
			InterviewDto dto = new InterviewDto();
			dto.setRequestId(requestId);
			dto.setEmployeeId(employeeId);
			dto.setCandidateId(candidateId);
			dto.setInterviewLevels(interviewLevels);
			dto.setCreatedByUserId(createdByUserId);
			
			List<LevelProgressDto> lp = new LinkedList<>();
			if (levels != null) {
				for (Map<String, Object> m : levels) {
					LevelProgressDto r = new LevelProgressDto();
					r.setLevel(asString(m.get("level")));
					r.setScheduledAtText(asString(m.get("scheduledAt")));
					r.setInterviewNotes(asString(m.get("interviewNotes")));
					r.setStatus("Scheduled");
					lp.add(r);
				}
			}
			dto.setLevelProgress(lp);
			InterviewDto out = service.createBatchNoInterviewer(dto);
			
			// Build Response
			ResourceRequest rr = rrRepo.findById(requestId).orElse(null);
			Long demandId = rr != null ? rr.getDemandId() : null;
			Long groupId = rr != null ? rr.getGroupId() : null;
			String isoNow = OffsetDateTime.now().toString();
			
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("interviewBatchId", out.getInterviewId());
			result.put("requestId", out.getRequestId());
			result.put("demandId", demandId);
			result.put("groupId", groupId);
			
			if (out.getEmployeeId() != null) {
				result.put("employeeId", out.getEmployeeId());
				result.put("employeeName", out.getEmployeeName());
				result.put("employeeEmail", out.getEmployeeEmail());
			} else if (out.getCandidateId() != null) {
				result.put("candidateId", out.getCandidateId());
				result.put("candidateName", out.getCandidateName());
				result.put("candidateEmail", out.getCandidateEmail());
			}
			
			result.put("createdByUserId", out.getCreatedByUserId());
			result.put("createdByUserName", out.getCreatedByUserName());
			result.put("createdAt", isoNow);
			result.put("overallStatus", out.getOverallStatus());
			result.put("onboardingStatus", out.getOnboardingStatus());
			Long projectId = out.getProjectId();
			String projectName = out.getProjectName();
			if (projectId == null && (projectName == null || projectName.isBlank()) && demandId != null) {
				Demand d = demandRepo.findById(demandId).orElse(null);
				if (d != null) {
					projectName = d.getProjectName();
					if (out.getCompanyId() == null && d.getCompanyId() != null) {
						result.put("companyId", d.getCompanyId());
					}
				}
			}
			result.put("projectId", projectId);
			result.put("projectName", projectName);
			result.put("companyId", out.getCompanyId());
			result.put("companyName", out.getCompanyName());
			result.put("accountId", out.getAccountId());
			result.put("accountName", out.getAccountName());
			result.put("interviewLevels", out.getInterviewLevels());
			List<Map<String, Object>> levelOut = new LinkedList<>();
			if (dto.getLevelProgress() != null) {
				for (LevelProgressDto r : dto.getLevelProgress()) {
					if (!StringUtils.hasText(r.getLevel()) || "__META__".equals(r.getLevel())) continue;
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("level", r.getLevel());
					row.put("scheduledAt", toIso(r.getScheduledAtText()));
					row.put("interviewNotes", r.getInterviewNotes());
					row.put("status", StringUtils.hasText(r.getStatus()) ? r.getStatus() : "Scheduled");
					levelOut.add(row);
				}
			}
			result.put("levels", levelOut);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.status(HttpStatus.CREATED).body(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	private static Long asLong(Object o) {
		if (o == null) return null;
		if (o instanceof Number n) return n.longValue();
		try { return Long.parseLong(o.toString()); } catch (Exception ignored) { return null; }
	}

	private static String asString(Object o) {
		return o == null ? null : o.toString();
	}

	private static String toIso(String txt) {
		if (!StringUtils.hasText(txt)) return null;
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("dd-MM-uuuu HH-mm");
			LocalDateTime ld = LocalDateTime.parse(txt, f);
			return ld.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
		} catch (Exception ignored) {}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");
			LocalDateTime ld = LocalDateTime.parse(txt, f);
			return ld.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
		} catch (Exception ignored) {}
		try {
			return OffsetDateTime.parse(txt).toString();
		} catch (Exception ignored) {}
		return null;
	}

	private static String cleanMsg(Exception e) {
		String m = e.getMessage();
		return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
	}
}
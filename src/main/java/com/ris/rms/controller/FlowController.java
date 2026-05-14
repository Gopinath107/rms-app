package com.ris.rms.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.dto.FlowRequest;
import com.ris.rms.service.CandidateService;
import com.ris.rms.service.FlowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FlowController {

	private final FlowService flowService;
	private final CandidateService candidateService;

	@GetMapping("/employeeFlows")
	public ResponseEntity<Map<String, Object>> listEmployeeFlows(@RequestParam(defaultValue = "0") Integer page,
			@RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) Long companyId,
			@RequestParam(required = false) String q, @RequestParam(required = false) String status,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String fromDate, @RequestParam(required = false) String toDate) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			LocalDate from = parseDateOrNull(fromDate);
			LocalDate to = parseDateOrNull(toDate);
			if (from != null && to != null && from.isAfter(to))
				throw new IllegalArgumentException("fromDate must be <= toDate");
			Map<String, Object> result = flowService.listEmployeeFlows(page, size, companyId, q, status, departmentId,
					from, to);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", java.util.List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", java.util.List
					.of(StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PostMapping("/flowEmployeeID")
	public ResponseEntity<Map<String, Object>> getEmployeeFlow(@RequestBody FlowRequest body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			int page = body.getPage() == null ? 0 : body.getPage();
			int size = body.getSize() == null ? 10 : body.getSize();
			LocalDate from = parseDateOrNull(body.getFromDate());
			LocalDate to = parseDateOrNull(body.getToDate());
			if (from != null && to != null && from.isAfter(to))
				throw new IllegalArgumentException("fromDate must be <= toDate");
			Map<String, Object> result = flowService.getEmployeeFlow(body.getEmployeeId(), page, size, from, to);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", java.util.List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors",
					java.util.List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@GetMapping("/candidateFlows")
	public ResponseEntity<Map<String, Object>> listCandidateFlows(@RequestParam(defaultValue = "0") Integer page,
			@RequestParam(defaultValue = "10") Integer size, @RequestParam(required = false) Long companyId,
			@RequestParam(required = false) String q, @RequestParam(required = false) String status,
			@RequestParam(required = false) String sourceType, // Replaces departmentId
			@RequestParam(required = false) String fromDate, @RequestParam(required = false) String toDate) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			LocalDate from = parseDateOrNull(fromDate);
			LocalDate to = parseDateOrNull(toDate);
			if (from != null && to != null && from.isAfter(to))
				throw new IllegalArgumentException("fromDate must be <= toDate");

			Map<String, Object> result = flowService.listCandidateFlows(page, size, companyId, q, status, sourceType,
					from, to);

			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", java.util.List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", java.util.List
					.of(StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PostMapping("/flowCandidateID")
	public ResponseEntity<Map<String, Object>> getCandidateFlow(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long candidateId = body.get("candidateId") != null ? Long.valueOf(body.get("candidateId").toString())
					: null;
			int page = body.get("page") != null ? (int) body.get("page") : 0;
			int size = body.get("size") != null ? (int) body.get("size") : 10;
			LocalDate from = parseDateOrNull((String) body.get("fromDate"));
			LocalDate to = parseDateOrNull((String) body.get("toDate"));

			if (from != null && to != null && from.isAfter(to))
				throw new IllegalArgumentException("fromDate must be <= toDate");

			Map<String, Object> result = flowService.getCandidateFlow(candidateId, page, size, from, to);

			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", java.util.List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors",
					java.util.List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	private LocalDate parseDateOrNull(String s) {
		if (!StringUtils.hasText(s))
			return null;
		try {
			return LocalDate.parse(s);
		} catch (DateTimeParseException ex) {
			throw new IllegalArgumentException("Invalid date format. Use ISO yyyy-MM-dd");
		}
	}
}
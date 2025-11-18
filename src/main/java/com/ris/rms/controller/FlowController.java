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
import com.ris.rms.service.FlowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FlowController {

    private final FlowService flowService;

  

    @GetMapping("/employeeFlows")
    public ResponseEntity<Map<String, Object>> listEmployeeFlows(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate
    ) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            LocalDate from = parseDateOrNull(fromDate);
            LocalDate to = parseDateOrNull(toDate);
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("fromDate must be <= toDate");
            Map<String, Object> result = flowService.listEmployeeFlows(page, size, companyId, q, status, departmentId, from, to);
            resp.put("result", result);
            resp.put("success", true);
            resp.put("errors", java.util.List.of());
            resp.put("errorCount", 0);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("result", null);
            resp.put("success", false);
            resp.put("errors", java.util.List.of(StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()));
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
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("fromDate must be <= toDate");
            Map<String, Object> result = flowService.getEmployeeFlow(body.getEmployeeId(), page, size, from, to);
            resp.put("result", result);
            resp.put("success", true);
            resp.put("errors", java.util.List.of());
            resp.put("errorCount", 0);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("result", null);
            resp.put("success", false);
            resp.put("errors", java.util.List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            resp.put("errorCount", 1);
            return ResponseEntity.badRequest().body(resp);
        }
    }

    private LocalDate parseDateOrNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format. Use ISO yyyy-MM-dd");
        }
        }
}

package com.ris.rms.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.service.ResReqDecisionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resource-requests")
public class ResReqDecisionController {

	private final ResReqDecisionService service;

	@PutMapping("/hr/DecideGroup")
	public ResponseEntity<Map<String, Object>> hrDecideGroup(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long groupId = asLong(body.get("groupId"));
			Long approverUserId = asLong(body.get("approverUserId"));
			String decision = asText(body.get("decision"));
			String comments = asText(body.get("comments"));

			if (groupId == null)
				throw new IllegalArgumentException("groupId is required");
			if (approverUserId == null)
				throw new IllegalArgumentException("approverUserId is required");

			Map<String, Object> result = service.hrDecideGroup(groupId, approverUserId, decision, comments);

			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/hr/Decide")
	public ResponseEntity<Map<String, Object>> hrDecide(@RequestBody Map<String, Object> body) {
	    Map<String, Object> resp = new LinkedHashMap<>();
	    try {
	        Long approverUserId = asLong(body.get("approverUserId"));
	        String decision = asText(body.get("decision"));
	        String comments = asText(body.get("comments"));

	        if (approverUserId == null) {
	            throw new IllegalArgumentException("approverUserId is required");
	        }

	        Object idsObj = body.get("requestIds");
	        if (!(idsObj instanceof List<?> rawList)) {
	            throw new IllegalArgumentException("'requestIds' must be a non-empty array");
	        }

	        List<Long> idsToProcess = new ArrayList<>();
	        for (Object o : rawList) {
	            Long val = asLong(o);
	            if (val != null) {
	                idsToProcess.add(val);
	            }
	        }

	        if (idsToProcess.isEmpty()) {
	            throw new IllegalArgumentException("'requestIds' must contain at least one valid id");
	        }

	        Map<String, Object> result = service.hrDecide(idsToProcess, approverUserId, decision, comments);

	        resp.put("result", result);
	        resp.put("success", true);
	        resp.put("errors", List.of());
	        resp.put("errorCount", 0);
	        return ResponseEntity.ok(resp);
	    } catch (Exception e) {
	        resp.put("result", null);
	        resp.put("success", false);
	        resp.put("errors", List.of(cleanMsg(e)));
	        resp.put("errorCount", 1);
	        return ResponseEntity.badRequest().body(resp);
	    }
	}

	private static String cleanMsg(Exception e) {
		String m = e.getMessage();
		return StringUtils.hasText(m) ? m : e.getClass().getSimpleName();
	}

	private static Long asLong(Object o) {
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.longValue();
		try {
			return Long.parseLong(o.toString());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String asText(Object o) {
		return o == null ? null : o.toString();
	}

	private static BigDecimal asBigDecimal(Object o) {
		if (o == null)
			return null;
		if (o instanceof Number n)
			return new BigDecimal(n.toString());
		try {
			return new BigDecimal(o.toString());
		} catch (Exception e) {
			return null;
		}
	}
}

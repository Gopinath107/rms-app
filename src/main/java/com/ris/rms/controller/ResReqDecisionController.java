package com.ris.rms.controller;

import com.ris.rms.service.ResReqDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	        String decision = asText(body.get("decision")); // Approved | Rejected
	        String comments = asText(body.get("comments"));

	        if (groupId == null) throw new IllegalArgumentException("groupId is required");
	        if (approverUserId == null) throw new IllegalArgumentException("approverUserId is required");

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
			Long requestId = asLong(body.get("requestId"));
			Long approverUserId = asLong(body.get("approverUserId"));
			String decision = asText(body.get("decision")); // Approved | Rejected
			String comments = asText(body.get("comments"));

			if (requestId == null)
				throw new IllegalArgumentException("requestId is required");
			if (approverUserId == null)
				throw new IllegalArgumentException("approverUserId is required");

			Map<String, Object> result = service.hrDecide(requestId, approverUserId, decision, comments);

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

package com.ris.rms.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ris.rms.dto.InterviewDto;
import com.ris.rms.service.InterviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interviews")
public class InterviewController {

	private final InterviewService service;

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> create(@RequestBody InterviewDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (dto.getRequestId() == null)
				throw new IllegalArgumentException("requestId is required");
			InterviewDto saved = service.create(dto);
			resp.put("result", saved);
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

	@PutMapping("/Update")
	public ResponseEntity<Map<String, Object>> update(@RequestBody InterviewDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (dto.getInterviewId() == null)
				throw new IllegalArgumentException("interviewId is required");
			if (dto.getRequestId() == null)
				throw new IllegalArgumentException("requestId is required");
			InterviewDto updated = service.updateWithRequestCheck(dto.getInterviewId(), dto.getRequestId(), dto);
			resp.put("result", updated);
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

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long requestId, @RequestParam(required = false) Long employeeId,
			@RequestParam(required = false) Long candidateId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String interviewType, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (id != null) {
				InterviewDto one = service.getById(id);
				if (requestId != null && !Objects.equals(one.getRequestId(), requestId)) {
					throw new IllegalArgumentException("Interview does not belong to given requestId");
				}
				resp.put("result", one);
			} else {
				List<InterviewDto> list = service.list(requestId, employeeId,candidateId, status, interviewType, page, size);

				// Extra filter for candidate if requested
				if (candidateId != null) {
					list = list.stream().filter(Objects::nonNull)
							.filter(i -> Objects.equals(i.getCandidateId(), candidateId)).toList();
				}

				resp.put("result", list);
			}

			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", id != null ? null : List.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return id != null ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp)
					: ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/LevelsComplete")
	public ResponseEntity<Map<String, Object>> completeLevels(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("interviewId"));
			Long requestId = asLong(body.get("requestId"));
			@SuppressWarnings("unchecked")
			List<String> levels = (List<String>) body.get("levels");
			String feedback = asString(body.get("feedback"));
			if (!StringUtils.hasText(feedback))
				feedback = asString(body.get("notes"));
			String decision = asString(body.get("status"));
			Long interviewerUserId = asLong(body.get("interviewerUserId"));

			if (id == null)
				throw new IllegalArgumentException("interviewId is required");
			if (requestId == null)
				throw new IllegalArgumentException("requestId is required");
			if (levels == null || levels.isEmpty())
				throw new IllegalArgumentException("levels is required");
			if (!StringUtils.hasText(decision))
				throw new IllegalArgumentException("status is required");
			String norm = decision.trim();
			if (!norm.equalsIgnoreCase("Selected") && !norm.equalsIgnoreCase("Rejected")) {
				throw new IllegalArgumentException("status must be Selected or Rejected");
			}

			InterviewDto out = service.completeLevels(id, requestId, levels, feedback, interviewerUserId, norm);
			resp.put("result", out);
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

	@PutMapping("/onboarding")
	public ResponseEntity<Map<String, Object>> onboarding(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long interviewId = asLong(body.get("interviewId"));
			if (interviewId == null)
				interviewId = asLong(body.get("interviewID"));
			String status = asString(body.get("status"));
			String note = asString(body.get("notes"));
			if (interviewId == null)
				throw new IllegalArgumentException("interviewID is required");
			if (!StringUtils.hasText(status))
				throw new IllegalArgumentException("status is required");
			InterviewDto out = service.updateOnboarding(interviewId, status, note);
			resp.put("result", out);
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

	@PutMapping("/Cancel")
	public ResponseEntity<Map<String, Object>> cancel(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("interviewId"));
			Long requestId = asLong(body.get("requestId"));
			String reason = asString(body.get("reason"));
			if (id == null)
				throw new IllegalArgumentException("interviewId is required");
			if (requestId == null)
				throw new IllegalArgumentException("requestId is required");
			InterviewDto out = service.cancel(id, requestId, reason);
			resp.put("result", out);
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

	@PutMapping("/NoShow")
	public ResponseEntity<Map<String, Object>> noShow(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("interviewId"));
			Long requestId = asLong(body.get("requestId"));
			String who = asString(body.get("who"));
			List<String> levels = (List<String>) body.get("levels");
			String feedback = asString(body.get("feedback"));
			if (!StringUtils.hasText(feedback))
				feedback = asString(body.get("notes"));
			if (id == null)
				throw new IllegalArgumentException("interviewId is required");
			if (requestId == null)
				throw new IllegalArgumentException("requestId is required");
			InterviewDto out = service.noShow(id, requestId, who, feedback, levels);
			resp.put("result", out);
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

	@DeleteMapping("/delete")
	public ResponseEntity<Map<String, Object>> delete(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("interviewId"));
			if (id == null)
				throw new IllegalArgumentException("interviewId is required");
			service.delete(id);
			resp.put("result", Map.of("deletedId", id));
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

	private static String asString(Object o) {
		return o == null ? null : o.toString();
	}
}
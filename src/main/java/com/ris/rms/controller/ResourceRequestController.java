package com.ris.rms.controller;

import com.ris.rms.dto.ResourceRequestDto;
import com.ris.rms.service.ResourceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resource-requests")
public class ResourceRequestController {

	private final ResourceRequestService service;

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ResourceRequestDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			ResourceRequestDto saved = service.create(dto);
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

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long groupId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String priority, @RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (id != null) {
				ResourceRequestDto one = service.getById(id);
				resp.put("result", one);
			} else {
				List<ResourceRequestDto> data = service.list(companyId, projectId, groupId, status, priority, q, page,
						size);
				resp.put("result", data);
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

	@PutMapping("/Update")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody ResourceRequestDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (dto.getRequestId() == null)
				throw new IllegalArgumentException("requestId is required");
			ResourceRequestDto updated = service.update(dto.getRequestId(), dto);
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

	@DeleteMapping("/delete")
	public ResponseEntity<Map<String, Object>> delete(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("requestId"));
			if (id == null)
				throw new IllegalArgumentException("requestId is required");
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
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp);
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
}

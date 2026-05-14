package com.ris.rms.controller;

import com.ris.rms.dto.CompanyDto;
import com.ris.rms.service.CompanyService;
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
@RequestMapping("/api/companies")
public class CompanyController {

	private final CompanyService service;

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CompanyDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			CompanyDto saved = service.create(dto);
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
	public ResponseEntity<Map<String, Object>> listGet(@RequestParam(required = false) Long id,
			@RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (id != null) {
				CompanyDto one = service.getById(id);
				resp.put("result", one);
			} else {
				List<CompanyDto> data = service.list(q, page, size);
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

	@PostMapping("/list")
	public ResponseEntity<Map<String, Object>> listPost(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = asLong(body.get("id"));
			String q = asString(body.get("q"));
			Integer page = asInt(body.get("page"));
			Integer size = asInt(body.get("size"));

			if (id != null) {
				CompanyDto one = service.getById(id);
				resp.put("result", one);
			} else {
				List<CompanyDto> data = service.list(q, page, size);
				resp.put("result", data);
			}
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", body.containsKey("id") ? null : List.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return body.containsKey("id") ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(resp)
					: ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/update")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody CompanyDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (dto.getCompanyId() == null) {
				throw new IllegalArgumentException("companyId is required");
			}
			CompanyDto updated = service.update(dto.getCompanyId(), dto);
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
			Long id = asLong(body.get("companyId"));
			if (id == null)
				throw new IllegalArgumentException("companyId is required");

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

	private static Integer asInt(Object o) {
		if (o == null)
			return null;
		if (o instanceof Number n)
			return n.intValue();
		try {
			return Integer.parseInt(o.toString());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String asString(Object o) {
		return o == null ? null : o.toString();
	}
}

package com.ris.rms.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
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

import com.ris.rms.dto.AccountDto;
import com.ris.rms.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {

	private static final String KEY_RESULT = "result";
	private static final String KEY_SUCCESS = "success";
	private static final String KEY_ERRORS = "errors";
	private static final String KEY_ERROR_COUNT = "errorCount";

	private static final String KEY_PAGE = "page";
	private static final String KEY_SIZE = "size";
	private static final String KEY_TOTAL_ELEMENTS = "totalElements";
	private static final String KEY_TOTAL_PAGES = "totalPages";

	private final AccountService service;

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AccountDto dto) {
		try {
			AccountDto saved = service.create(dto);
			saved.setRelationshipEndDate(null);

			return ResponseEntity.status(HttpStatus.CREATED).body(buildSuccessResponse(saved));
		} catch (Exception e) {

			return ResponseEntity.badRequest().body(buildErrorResponse(e, null));
		}
	}

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long companyId,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") Integer page,
			@RequestParam(defaultValue = "10") Integer size) {

		try {
			Page<AccountDto> data = service.list(companyId, q, page, size);

			Map<String, Object> resp = buildSuccessResponse(data.getContent());

			resp.put(KEY_PAGE, data.getNumber());
			resp.put(KEY_SIZE, data.getSize());
			resp.put(KEY_TOTAL_ELEMENTS, data.getTotalElements());
			resp.put(KEY_TOTAL_PAGES, data.getTotalPages());

			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(buildErrorResponse(e, List.of()));
		}
	}

	@PutMapping("/update")
	public ResponseEntity<Map<String, Object>> update(@Valid @RequestBody AccountDto dto) {
		try {
			if (dto.getAccountId() == null) {
				throw new IllegalArgumentException("accountId is required");
			}
			AccountDto updated = service.update(dto.getAccountId(), dto);
			return ResponseEntity.ok(buildSuccessResponse(updated));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(buildErrorResponse(e, null));
		}
	}

	@DeleteMapping("/delete")
	public ResponseEntity<Map<String, Object>> delete(@RequestBody Map<String, Object> body) {
		try {
			Long id = asLong(body.get("accountId"));
			if (id == null)
				throw new IllegalArgumentException("accountId is required");

			service.delete(id);

			return ResponseEntity.ok(buildSuccessResponse(Map.of("deletedId", id)));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(e, null));
		}
	}

	private Map<String, Object> buildSuccessResponse(Object result) {
		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put(KEY_RESULT, result);
		resp.put(KEY_SUCCESS, true);
		resp.put(KEY_ERRORS, List.of());
		resp.put(KEY_ERROR_COUNT, 0);
		return resp;
	}

	private Map<String, Object> buildErrorResponse(Exception e, Object defaultResult) {
		Map<String, Object> resp = new LinkedHashMap<>();
		resp.put(KEY_RESULT, defaultResult);
		resp.put(KEY_SUCCESS, false);
		resp.put(KEY_ERRORS, List.of(cleanMsg(e)));
		resp.put(KEY_ERROR_COUNT, 1);
		return resp;
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
package com.ris.rms.controller;

import com.ris.rms.dto.AllocationDto;
import com.ris.rms.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/allocations")
public class AllocationController {

	private final AllocationService service;

	@PostMapping("/create")
	public ResponseEntity<Map<String, Object>> create(@RequestBody AllocationDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			AllocationDto saved = service.create(dto);
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

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) Long projectId,
			@RequestParam(required = false) Long employeeId, @RequestParam(required = false) Long candidateId,
			@RequestParam(required = false) String status, @RequestParam(required = false) Boolean billable,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (id != null) {
				resp.put("result", service.getById(id));
			} else {
				List<AllocationDto> list = service.list(companyId, projectId, employeeId,candidateId, status, billable, page, size);

				
				if (candidateId != null) {
					list = list.stream().filter(Objects::nonNull)
							.filter(a -> Objects.equals(a.getCandidateId(), candidateId)).toList();
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
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PutMapping("/Update")
	public ResponseEntity<Map<String, Object>> update(@RequestBody AllocationDto dto) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (dto.getAllocationId() == null)
				throw new IllegalArgumentException("allocationId is required");
			AllocationDto updated = service.update(dto.getAllocationId(), dto);
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

	@DeleteMapping("/delete")
	public ResponseEntity<Map<String, Object>> delete(@RequestBody Map<String, Object> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = body.get("allocationId") == null ? null : Long.valueOf(body.get("allocationId").toString());
			if (id == null)
				throw new IllegalArgumentException("allocationId is required");
			service.delete(id);
			resp.put("result", Map.of("deletedId", id));
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
}
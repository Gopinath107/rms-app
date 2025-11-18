package com.ris.rms.controller;

import com.ris.rms.dto.StatusOptionDto;
import com.ris.rms.service.StatusMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interviews/meta")
public class StatusMetaController {

	private final StatusMetaService statusService;

	@GetMapping("/statuses")
	public ResponseEntity<Map<String, Object>> getStatuses(@RequestParam(required = false) String category) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (StringUtils.hasText(category)) {
				List<StatusOptionDto> list = statusService.getByCategory(category);
				resp.put("result", list);
			} else {
				Map<String, List<StatusOptionDto>> grouped = statusService.getAllActiveGrouped();
				resp.put("result", grouped);
			}
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", StringUtils.hasText(category) ? List.of() : Map.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	private static String cleanMsg(Exception e) {
		String m = e.getMessage();
		return (m != null && !m.isBlank()) ? m : e.getClass().getSimpleName();
	}
}

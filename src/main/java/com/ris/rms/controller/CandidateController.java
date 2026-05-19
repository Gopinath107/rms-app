package com.ris.rms.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.dto.CandidateDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.service.CandidateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateService candidateService;
	private static final ObjectMapper OM = new ObjectMapper();

	@PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
	public ResponseEntity<Map<String, Object>> createCandidate(@Valid @ModelAttribute CandidateDto dto,
			@RequestParam(value = "skillIds", required = false) List<Long> skillIds,
			@RequestParam(value = "skillNames", required = false) List<String> skillNames,
			@RequestParam(value = "primarySkills", required = false) String primarySkills,
			@RequestParam(value = "secondarySkills", required = false) String secondarySkills,
			@RequestPart(value = "resume", required = false) MultipartFile resume,
			@RequestPart(value = "documentFiles", required = false) List<MultipartFile> documentFiles,
			@RequestParam(value = "documentData", required = false) String documentData) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			applySkillPayload(dto, skillIds, skillNames, primarySkills, secondarySkills);
			sanitizeDto(dto);
			CandidateDto result = candidateService.create(dto, resume, documentFiles, documentData);
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

	@PutMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
	public ResponseEntity<Map<String, Object>> updateCandidate(@Valid @ModelAttribute CandidateDto dto,
			@RequestParam(value = "skillIds", required = false) List<Long> skillIds,
			@RequestParam(value = "skillNames", required = false) List<String> skillNames,
			@RequestParam(value = "primarySkills", required = false) String primarySkills,
			@RequestParam(value = "secondarySkills", required = false) String secondarySkills,
			@RequestPart(value = "resume", required = false) MultipartFile resume,
			@RequestPart(value = "documentFiles", required = false) List<MultipartFile> documentFiles,
			@RequestParam(value = "documentData", required = false) String documentData) {
		sanitizeDto(dto);
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			applySkillPayload(dto, skillIds, skillNames, primarySkills, secondarySkills);
			Long id = dto.getCandidateId();
			if (id == null) {
				throw new IllegalArgumentException("candidateId is required for update");
			}

			CandidateDto result = candidateService.update(id, dto, resume, documentFiles, documentData);
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

	@GetMapping("/{id}")
	public ResponseEntity<Map<String, Object>> getCandidate(@PathVariable("id") Long id) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			CandidateDto result = candidateService.getById(id);
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

	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> listCandidates(
			@RequestParam(value = "companyId", required = false) Long companyId,
			@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "sourceType", required = false) String sourceType,
			@RequestParam(value = "page", required = false) Integer page,
			@RequestParam(value = "size", required = false) Integer size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			List<CandidateDto> result = candidateService.list(companyId, q, status, sourceType, page, size);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok(resp);
		} catch (Exception e) {
			resp.put("result", List.of());
			resp.put("success", false);
			resp.put("errors", List.of(cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}
	}

	@PostMapping(value = "/delete", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Map<String, Object>> deleteCandidate(@RequestBody Map<String, Long> body) {
		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			Long id = body.get("candidateId");
			if (id == null) {
				throw new IllegalArgumentException("candidateId is required for delete");
			}
			candidateService.delete(id);
			resp.put("result", null);
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

	@PostMapping("/share-resume")
	public ResponseEntity<Map<String, Object>> shareResume(@Valid @RequestBody ResumeShareDto shareRequest) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			ResumeShareDto result = candidateService.shareResume(shareRequest);
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

	@GetMapping("/{candidateId}/viewResume")
	public ResponseEntity<?> getCandidateResume(@PathVariable Long candidateId) {
		try {
			var resumeData = candidateService.getResumeByCandidateId(candidateId);
			Resource resource = resumeData.resource();
			String mimeType = resumeData.mimeType();
			String fileName = resumeData.fileName();

			return ResponseEntity.ok().contentType(MediaType.parseMediaType(mimeType))
					.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"").body(resource);

		} catch (Exception e) {
			if (e instanceof IllegalArgumentException) {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("result", null);
				body.put("success", false);
				body.put("errors", List.of(e.getMessage()));
				body.put("errorCount", 1);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
			}
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	@PostMapping(path = "/importExcel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Map<String, Object>> importCandidates(@RequestParam("file") MultipartFile file,
			@RequestParam("companyId") Long companyId) {

		Map<String, Object> resp = new LinkedHashMap<>();
		if (file.isEmpty()) {
			resp.put("success", false);
			resp.put("errors", List.of("Please select a file to upload."));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}

		try {
			ImportResultDto result = candidateService.importCandidates(companyId, file.getInputStream(),
					file.getOriginalFilename());

			resp.put("result", result);
			resp.put("success", result.getFailureCount() == 0);
			resp.put("errors", result.getErrors());
			resp.put("errorCount", result.getFailureCount());

			HttpStatus status = (result.getFailureCount() > 0 && result.getSuccessCount() > 0) ? HttpStatus.MULTI_STATUS
					: HttpStatus.CREATED;

			if (result.getSuccessCount() == 0 && result.getFailureCount() > 0) {
				status = HttpStatus.BAD_REQUEST;
			}

			return ResponseEntity.status(status).body(resp);

		} catch (IllegalArgumentException e) {
			resp.put("success", false);
			resp.put("errors", List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		} catch (Exception e) {
			resp.put("success", false);
			resp.put("errors", List.of("An unexpected error occurred during import: " + cleanMsg(e)));
			resp.put("errorCount", 1);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
		}
	}

	private String cleanMsg(Throwable e) {
		if (e == null)
			return "Unknown error";
		String msg = e.getMessage();
		if (msg == null || msg.isBlank())
			return e.toString();
		return msg;
	}

	private void applySkillPayload(CandidateDto dto, List<Long> skillIds, List<String> skillNames, String primarySkills,
			String secondarySkills) {
		if (skillIds != null && !skillIds.isEmpty()) {
			dto.setSkillIds(skillIds);
		}

		Set<String> mergedNames = new LinkedHashSet<>();
		if (skillNames != null) {
			skillNames.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(mergedNames::add);
		}
		mergedNames.addAll(parseSkillJson(primarySkills));
		mergedNames.addAll(parseSkillJson(secondarySkills));

		if (!mergedNames.isEmpty()) {
			dto.setSkillNames(new ArrayList<>(mergedNames));
		}
	}

	private List<String> parseSkillJson(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<String> parsed = OM.readValue(json, new TypeReference<List<String>>() {
			});
			return parsed.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
		} catch (Exception e) {
			return List.of();
		}
	}
	
	private void sanitizeDto(CandidateDto dto) {
	    if (dto.getPersonalEmailId() != null && dto.getPersonalEmailId().isBlank()) {
	        dto.setPersonalEmailId(null);
	    }
	}
}

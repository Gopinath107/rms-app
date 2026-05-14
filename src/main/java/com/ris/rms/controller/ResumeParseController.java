package com.ris.rms.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.service.ResumeParseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/resume")
public class ResumeParseController {

    private final ResumeParseService resumeParseService;

    /**
     * POST /api/resume/parse
     * Accepts a multipart resume file (PDF, DOC, DOCX).
     * Extracts text via Apache Tika, then parses structured fields via LLM or regex.
     *
     * @param resume multipart file upload
     * @return structured JSON with parsed candidate fields
     */
    @PostMapping(path = "/parse", consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<Map<String, Object>> parseResume(
            @RequestParam(name = "resume") MultipartFile resume) {

        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            Map<String, Object> parsedData = resumeParseService.parse(resume);

            resp.put("result", parsedData);
            resp.put("success", true);
            resp.put("errors", List.of());
            resp.put("errorCount", 0);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            // Validation errors (wrong file type, too large, etc.)
            log.warn("Resume parse validation error: {}", e.getMessage());
            resp.put("result", null);
            resp.put("success", false);
            resp.put("errors", List.of(e.getMessage()));
            resp.put("errorCount", 1);
            return ResponseEntity.badRequest().body(resp);

        } catch (Exception e) {
            log.error("Resume parsing failed unexpectedly", e);
            resp.put("result", null);
            resp.put("success", false);
            resp.put("errors", List.of("Could not parse resume. You can fill the details manually."));
            resp.put("errorCount", 1);
            return ResponseEntity.internalServerError().body(resp);
        }
    }
}

package com.ris.rms.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.dto.EmployeeDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees")
public class EmployeeController {

	private final EmployeeService service;

	@PostMapping(path = "/create", consumes = "multipart/form-data", produces = "application/json")
	public ResponseEntity<Map<String, Object>> createMultipart(@RequestParam Long companyId,
			@RequestParam String firstName, @RequestParam String lastName, @RequestParam(required = false) String email,
			@RequestParam(required = false) String phoneNumber, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Integer experienceYears, @RequestParam(required = false) String location,
			@RequestParam(required = false) String joiningDate, @RequestParam(required = false) String employmentType,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) java.util.List<Long> skillIds,
			@RequestPart(required = false, name = "resume") org.springframework.web.multipart.MultipartFile resume,
			@RequestParam(required = false) Long currentProjectId,
			@RequestParam(required = false) Long currentAccountId, @RequestParam(required = false) String gender,
			@RequestParam(required = false) String personalEmailId, @RequestParam(required = false) String degrees,
			@RequestParam(required = false) String specialization,
			@RequestParam(required = false) Integer yearOfPassing,
			@RequestParam(required = false) String profileSummary,
			@RequestParam(required = false) String trainingSummary,
			@RequestParam(required = false) String certificationSummary,
			// Phase 9 new fields
			@RequestParam(required = false) String middleName,
			@RequestParam(required = false) String dateOfBirth,
			@RequestParam(required = false) String primaryCountryCode,
			@RequestParam(required = false) String primaryContactNo,
			@RequestParam(required = false) String secondaryCountryCode,
			@RequestParam(required = false) String secondaryContactNo,
			@RequestParam(required = false) String countryOfCitizenship,
			@RequestParam(required = false) String documentType,
			@RequestParam(required = false) String documentNumber,
			@RequestParam(required = false) String securityClearance,
			@RequestParam(required = false) String visa,
			@RequestParam(required = false) String visaType,
			@RequestParam(required = false) String country,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String city,
			@RequestParam(required = false) String zipCode,
			@RequestParam(required = false) String street,
			@RequestParam(required = false) String availabilityToJoin,
			@RequestParam(required = false) String interviewAvailability,
			@RequestParam(required = false) String highestQualification,
			@RequestParam(required = false) String universityName,
			@RequestParam(required = false) String dateOfQualification,
			@RequestParam(required = false) String usaDegree,
			@RequestParam(required = false) String currentJobTitle,
			@RequestParam(required = false) String mostRecentEmployer,
			@RequestParam(required = false) Integer totalExperience,
			@RequestParam(required = false) String relocate,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) String frequency,
			@RequestParam(required = false) java.math.BigDecimal sourcingRate,
			@RequestParam(required = false) String resumeSummary,
			@RequestParam(required = false) String suggestedKeywords,
			@RequestParam(required = false) String primarySkills,
			@RequestParam(required = false) String secondarySkills,
			@RequestParam(required = false) String socialLinks) {
		var resp = new java.util.LinkedHashMap<String, Object>();
		try {
			var dto = new EmployeeDto();
			dto.setCompanyId(companyId);
			dto.setFirstName(firstName);
			dto.setMiddleName(middleName);
			dto.setLastName(lastName);
			dto.setEmail(email);
			dto.setPhoneNumber(phoneNumber);
			dto.setDepartmentId(departmentId);
			dto.setExperienceYears(experienceYears);
			dto.setLocation(location);
			if (joiningDate != null && !joiningDate.isBlank()) {
			    dto.setJoiningDate(parseJoiningDateFlexible(joiningDate));
			}
			dto.setEmploymentType(employmentType);
			dto.setStatus(status);
			dto.setSkillIds(skillIds);
			dto.setCurrentProjectId(currentProjectId);
			dto.setCurrentAccountId(currentAccountId);
			dto.setGender(gender);
			dto.setPersonalEmailId(personalEmailId);
			dto.setDegrees(degrees);
			dto.setSpecialization(specialization);
			dto.setYearOfPassing(yearOfPassing);
			dto.setProfileSummary(profileSummary);
			dto.setTrainingSummary(trainingSummary);
			dto.setCertificationSummary(certificationSummary);
			// Phase 9 new fields
			dto.setPrimaryCountryCode(primaryCountryCode);
			dto.setPrimaryContactNo(primaryContactNo);
			dto.setSecondaryCountryCode(secondaryCountryCode);
			dto.setSecondaryContactNo(secondaryContactNo);
			if (dateOfBirth != null && !dateOfBirth.isBlank()) {
				try { dto.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth)); } catch (Exception ignored) {}
			}
			dto.setCountryOfCitizenship(countryOfCitizenship);
			dto.setDocumentType(documentType);
			dto.setDocumentNumber(documentNumber);
			dto.setSecurityClearance(securityClearance);
			dto.setVisa(visa);
			dto.setVisaType(visaType);
			dto.setCountry(country);
			dto.setState(state);
			dto.setCity(city);
			dto.setZipCode(zipCode);
			dto.setStreet(street);
			dto.setAvailabilityToJoin(availabilityToJoin);
			dto.setInterviewAvailability(interviewAvailability);
			dto.setHighestQualification(highestQualification);
			dto.setUniversityName(universityName);
			if (dateOfQualification != null && !dateOfQualification.isBlank()) {
				try { dto.setDateOfQualification(java.time.LocalDate.parse(dateOfQualification)); } catch (Exception ignored) {}
			}
			dto.setUsaDegree(usaDegree);
			dto.setCurrentJobTitle(currentJobTitle);
			dto.setMostRecentEmployer(mostRecentEmployer);
			dto.setTotalExperience(totalExperience);
			dto.setRelocate(relocate);
			dto.setCurrency(currency);
			dto.setFrequency(frequency);
			dto.setSourcingRate(sourcingRate);
			dto.setResumeSummary(resumeSummary);
			dto.setSuggestedKeywords(suggestedKeywords);
			// Deserialize JSON arrays
			com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
			if (primarySkills != null && !primarySkills.isBlank()) {
				try { dto.setPrimarySkills(om.readValue(primarySkills, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {})); } catch (Exception ignored) {}
			}
			if (secondarySkills != null && !secondarySkills.isBlank()) {
				try { dto.setSecondarySkills(om.readValue(secondarySkills, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {})); } catch (Exception ignored) {}
			}
			if (socialLinks != null && !socialLinks.isBlank()) {
				try { dto.setSocialLinks(om.readValue(socialLinks, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, String>>>() {})); } catch (Exception ignored) {}
			}
			var saved = service.create(dto, resume);

			resp.put("result", saved);
			resp.put("success", true);
			resp.put("errors", java.util.List.of());
			resp.put("errorCount", 0);
			return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
					.body(resp);

		} catch (Exception e) {
			resp.put("result", null);
			resp.put("success", false);
			resp.put("errors", java.util.List.of(e.getMessage()));
			resp.put("errorCount", 1);
			return org.springframework.http.ResponseEntity.badRequest().body(resp);
		}
	}


	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long id,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) String q,
			@RequestParam(required = false) String status, @RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			if (id != null) {
				EmployeeDto one = service.getById(id);
				resp.put("result", one);
			} else {
				List<EmployeeDto> data = service.list(companyId, q, status, departmentId, page, size);
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

	@PutMapping(path = "/Update", consumes = "multipart/form-data", produces = "application/json")
	public ResponseEntity<Map<String, Object>> updateMultipart(@RequestParam Long employeeId,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) String firstName,
			@RequestParam(required = false) String middleName,
			@RequestParam(required = false) String lastName, @RequestParam(required = false) String phoneNumber,
			@RequestParam(required = false) Long departmentId, @RequestParam(required = false) Integer experienceYears,
			@RequestParam(required = false) String location, @RequestParam(required = false) String joiningDate,
			@RequestParam(required = false) String employmentType, @RequestParam(required = false) String status,
			@RequestParam(required = false) java.util.List<Long> skillIds,
			@RequestPart(required = false, name = "resume") org.springframework.web.multipart.MultipartFile resume,
			@RequestParam(required = false) Long currentProjectId,
			@RequestParam(required = false) Long currentAccountId, @RequestParam(required = false) String gender,
			@RequestParam(required = false) String personalEmailId,
			@RequestParam(required = false) String degrees, @RequestParam(required = false) String specialization,
			@RequestParam(required = false) Integer yearOfPassing,
			@RequestParam(required = false) String profileSummary,
			@RequestParam(required = false) String trainingSummary,
			@RequestParam(required = false) String certificationSummary,
			// Phase 9 new fields
			@RequestParam(required = false) String dateOfBirth,
			@RequestParam(required = false) String primaryCountryCode,
			@RequestParam(required = false) String primaryContactNo,
			@RequestParam(required = false) String secondaryCountryCode,
			@RequestParam(required = false) String secondaryContactNo,
			@RequestParam(required = false) String countryOfCitizenship,
			@RequestParam(required = false) String documentType,
			@RequestParam(required = false) String documentNumber,
			@RequestParam(required = false) String securityClearance,
			@RequestParam(required = false) String visa,
			@RequestParam(required = false) String visaType,
			@RequestParam(required = false) String country,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String city,
			@RequestParam(required = false) String zipCode,
			@RequestParam(required = false) String street,
			@RequestParam(required = false) String availabilityToJoin,
			@RequestParam(required = false) String interviewAvailability,
			@RequestParam(required = false) String highestQualification,
			@RequestParam(required = false) String universityName,
			@RequestParam(required = false) String dateOfQualification,
			@RequestParam(required = false) String usaDegree,
			@RequestParam(required = false) String currentJobTitle,
			@RequestParam(required = false) String mostRecentEmployer,
			@RequestParam(required = false) Integer totalExperience,
			@RequestParam(required = false) String relocate,
			@RequestParam(required = false) String currency,
			@RequestParam(required = false) String frequency,
			@RequestParam(required = false) java.math.BigDecimal sourcingRate,
			@RequestParam(required = false) String resumeSummary,
			@RequestParam(required = false) String suggestedKeywords,
			@RequestParam(required = false) String primarySkills,
			@RequestParam(required = false) String secondarySkills,
			@RequestParam(required = false) String socialLinks) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			var dto = new EmployeeDto();
			dto.setEmployeeId(employeeId);
			dto.setCompanyId(companyId);
			dto.setFirstName(firstName);
			dto.setMiddleName(middleName);
			dto.setLastName(lastName);
			dto.setPhoneNumber(phoneNumber);
			dto.setDepartmentId(departmentId);
			dto.setExperienceYears(experienceYears);
			dto.setLocation(location);
			if (joiningDate != null && !joiningDate.isBlank()) {
			    dto.setJoiningDate(parseJoiningDateFlexible(joiningDate));
			}
			dto.setEmploymentType(employmentType);
			dto.setStatus(status);
			dto.setSkillIds(skillIds);
			dto.setCurrentProjectId(currentProjectId);
			dto.setCurrentAccountId(currentAccountId);
			dto.setGender(gender);
			dto.setPersonalEmailId(personalEmailId);
			dto.setDegrees(degrees);
			dto.setSpecialization(specialization);
			dto.setYearOfPassing(yearOfPassing);
			dto.setProfileSummary(profileSummary);
			dto.setTrainingSummary(trainingSummary);
			dto.setCertificationSummary(certificationSummary);
			// Phase 9 new fields
			dto.setPrimaryCountryCode(primaryCountryCode);
			dto.setPrimaryContactNo(primaryContactNo);
			dto.setSecondaryCountryCode(secondaryCountryCode);
			dto.setSecondaryContactNo(secondaryContactNo);
			if (dateOfBirth != null && !dateOfBirth.isBlank()) {
				try { dto.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth)); } catch (Exception ignored) {}
			}
			dto.setCountryOfCitizenship(countryOfCitizenship);
			dto.setDocumentType(documentType);
			dto.setDocumentNumber(documentNumber);
			dto.setSecurityClearance(securityClearance);
			dto.setVisa(visa);
			dto.setVisaType(visaType);
			dto.setCountry(country);
			dto.setState(state);
			dto.setCity(city);
			dto.setZipCode(zipCode);
			dto.setStreet(street);
			dto.setAvailabilityToJoin(availabilityToJoin);
			dto.setInterviewAvailability(interviewAvailability);
			dto.setHighestQualification(highestQualification);
			dto.setUniversityName(universityName);
			if (dateOfQualification != null && !dateOfQualification.isBlank()) {
				try { dto.setDateOfQualification(java.time.LocalDate.parse(dateOfQualification)); } catch (Exception ignored) {}
			}
			dto.setUsaDegree(usaDegree);
			dto.setCurrentJobTitle(currentJobTitle);
			dto.setMostRecentEmployer(mostRecentEmployer);
			dto.setTotalExperience(totalExperience);
			dto.setRelocate(relocate);
			dto.setCurrency(currency);
			dto.setFrequency(frequency);
			dto.setSourcingRate(sourcingRate);
			dto.setResumeSummary(resumeSummary);
			dto.setSuggestedKeywords(suggestedKeywords);
			com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
			if (primarySkills != null && !primarySkills.isBlank()) {
				try { dto.setPrimarySkills(om.readValue(primarySkills, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {})); } catch (Exception ignored) {}
			}
			if (secondarySkills != null && !secondarySkills.isBlank()) {
				try { dto.setSecondarySkills(om.readValue(secondarySkills, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {})); } catch (Exception ignored) {}
			}
			if (socialLinks != null && !socialLinks.isBlank()) {
				try { dto.setSocialLinks(om.readValue(socialLinks, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, String>>>() {})); } catch (Exception ignored) {}
			}

			EmployeeDto updated = service.update(employeeId, dto, resume);

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
			Long id = asLong(body.get("employeeId"));
			if (id == null)
				throw new IllegalArgumentException("employeeId is required");

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

	@PostMapping(path = "/importExcel", consumes = "multipart/form-data")
	public ResponseEntity<Map<String, Object>> importEmployees(@RequestParam("file") MultipartFile file,
			@RequestParam("companyId") Long companyId) {
		Map<String, Object> resp = new LinkedHashMap<>();
		if (file.isEmpty()) {
			resp.put("success", false);
			resp.put("errors", List.of("Please select a file to upload."));
			resp.put("errorCount", 1);
			return ResponseEntity.badRequest().body(resp);
		}

		try {
			ImportResultDto result = service.importEmployees(companyId, file.getInputStream(),
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
			resp.put("errors", List.of("An unexpected error occurred during import: " + e.getMessage()));
			resp.put("errorCount", 1);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
		}
	}

	@GetMapping("/{employeeId}/viewResume")
	public ResponseEntity<?> getResume(@PathVariable Long employeeId) {
		try {
			var resumeData = service.getResumeByEmployeeId(employeeId);
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

	@PostMapping("/share-resume")
	public ResponseEntity<Map<String, Object>> shareResume(@Valid @RequestBody ResumeShareDto shareRequest) {

		Map<String, Object> resp = new LinkedHashMap<>();
		try {
			ResumeShareDto result = service.shareResume(shareRequest);
			resp.put("result", result);
			resp.put("success", true);
			resp.put("errors", List.of());
			resp.put("errorCount", 0);
			return ResponseEntity.ok().body(resp);
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


	private static LocalDate parseJoiningDateFlexible(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}

		dateStr = dateStr.trim();

		List<DateTimeFormatter> formatters = java.util.Arrays.asList(DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
				DateTimeFormatter.ofPattern("dd/MM/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"),
				DateTimeFormatter.ofPattern("MM/dd/yyyy"), DateTimeFormatter.ofPattern("M/d/yyyy"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd"));

		for (DateTimeFormatter fmt : formatters) {
			try {
				return LocalDate.parse(dateStr, fmt);
			} catch (DateTimeParseException ignored) {

			}
		}

		throw new IllegalArgumentException("Invalid joiningDate format: '" + dateStr
				+ "'. Allowed formats: yyyy-MM-dd, dd-MM-yyyy, dd/MM/yyyy, MM/dd/yyyy, yyyy/MM/dd");
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
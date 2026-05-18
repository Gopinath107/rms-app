package com.ris.rms.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.documents4j.api.DocumentType;
import com.documents4j.api.IConverter;
import com.documents4j.job.LocalConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ris.rms.dto.CandidateDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Candidate;
import com.ris.rms.entity.CandidateDocument;
import com.ris.rms.entity.CandidateSkill;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequestGroup;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.StatusMaster;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.CandidateDocumentRepository;
import com.ris.rms.repository.CandidateRepository;
import com.ris.rms.repository.CandidateSkillRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.StatusMasterRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.CandidateService;
import com.ris.rms.service.DemandService;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.ResumeStorageService;
import com.ris.rms.service.ResumeStorageService.ResumeResource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

	private final CandidateRepository candidateRepo;
	private final CandidateSkillRepository candidateSkillRepo;
	private final CandidateDocumentRepository candidateDocumentRepo;
	private final SkillRepository skillRepo;
	private final CompanyRepository companyRepo;
	private final ResumeStorageService storage;
	private final StatusMasterRepository statusMasterRepo;
	private final UserAccountRepository userAccountRepo;
	private final ResReqGroupRepository resReqGroupRepo;
	private final ProjectRepository projectRepo;
	private final AccountRepository accountRepo;
	private final DemandRepository demandRepo;
	private final DemandService demandService;
	private final EmailService emailService;
	private final EmployeeRepository employeeRepo;
	private final ResourceRequestRepository rrRepo;

	private static final long MAX_RESUME_BYTES = 10L * 1024 * 1024;
	private static final ObjectMapper OM = new ObjectMapper();

	@Override
	@Transactional(readOnly = true)
	public ResumeResource getResumeByCandidateId(Long candidateId) throws Exception {
		CandidateDocument doc = candidateDocumentRepo.findPrimaryResume(candidateId).orElseThrow(
				() -> new IllegalArgumentException("Resume not found for external candidate: " + candidateId));

		ResumeResource original = storage.load(doc);

		String fileName = original.fileName();
		String mimeType = original.mimeType() == null ? "" : original.mimeType();
		String lower = fileName == null ? "" : fileName.toLowerCase();

		if ("application/pdf".equalsIgnoreCase(mimeType) || lower.endsWith(".pdf")) {
			return original;
		}

		boolean isDocx = lower.endsWith(".docx")
				|| "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(mimeType);
		boolean isDoc = lower.endsWith(".doc") || "application/msword".equalsIgnoreCase(mimeType);

		if (!(isDoc || isDocx)) {
			return original;
		}

		IConverter converter = null;
		try (InputStream in = original.resource().getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			converter = LocalConverter.builder().build();
			DocumentType inType = isDocx ? DocumentType.DOCX : DocumentType.DOC;

			boolean ok = converter.convert(in).as(inType).to(out).as(DocumentType.PDF).prioritizeWith(1000).schedule()
					.get();
			if (!ok)
				throw new IllegalStateException("Resume conversion to PDF failed");

			byte[] pdf = out.toByteArray();
			String pdfName = replaceExt(fileName, ".pdf");
			Resource pdfRes = new ByteArrayResource(pdf) {
				@Override
				public String getFilename() {
					return pdfName;
				}
			};
			return new ResumeStorageService.ResumeResource(pdfRes, "application/pdf", pdfName);
		} finally {
			if (converter != null) {
				try {
					converter.shutDown();
				} catch (Exception ignore) {
				}
			}
		}
	}

	@Override
	@Transactional
	public ResumeShareDto shareResume(ResumeShareDto request) throws Exception {

		StatusMaster status = statusMasterRepo.findByCategoryAndCode("RESUME", request.getStatus())
				.orElseThrow(() -> new IllegalArgumentException("Invalid status code: " + request.getStatus()));

		Long candidateId = request.getCandidateId();
		Candidate candidate = candidateRepo.findById(candidateId)
				.orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + candidateId));

		String candidateName = ((candidate.getFirstName() == null ? "" : candidate.getFirstName()) + " "
				+ (candidate.getLastName() == null ? "" : candidate.getLastName())).trim();

		request.setCandidateName(candidateName);

		String statusCode = status.getCode();
		request.setStatusSet(statusCode);

		UserAccount actionUser = userAccountRepo.findById(request.getActionByUserId()).orElseThrow(
				() -> new IllegalArgumentException("Action user not found: " + request.getActionByUserId()));

		String actionByUserName = null;
		if (actionUser.getEmployeeId() != null) {
			actionByUserName = employeeRepo.findById(actionUser.getEmployeeId())
					.map(e -> ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
							+ (e.getLastName() == null ? "" : e.getLastName())).trim())
					.filter(s -> !s.isBlank()).orElse(null);
		}
		if (actionByUserName == null || actionByUserName.isBlank()) {
			actionByUserName = actionUser.getEmail();
		}

		request.setActionByUserName(actionByUserName);
		request.setActionAt(ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime());

		CandidateDocument document = candidateDocumentRepo.findPrimaryResume(candidateId)
				.orElseThrow(() -> new IllegalArgumentException(
						"No resume available for this candidate. Please upload a resume to proceed."));

		boolean shouldEmail = "Shared".equalsIgnoreCase(statusCode);

		ResumeStorageService.ResumeResource resumeResource = null;
		if (shouldEmail) {
			resumeResource = storage.load(document);
			if (resumeResource == null) {
				throw new IllegalStateException("Failed to load resume file from storage");
			}
		}

		List<Map<String, Object>> history = new ArrayList<>();
		if (document.getResumeShareMeta() != null && !document.getResumeShareMeta().isBlank()) {
			try {
				Map<String, Object> oldMeta = OM.readValue(document.getResumeShareMeta(),
						new TypeReference<Map<String, Object>>() {
						});
				if (oldMeta.containsKey("sharedWith") && oldMeta.get("sharedWith") instanceof List) {
					history = (List<Map<String, Object>>) oldMeta.get("sharedWith");
				}
			} catch (Exception e) {
			}
		}

		List<Map<String, Object>> newShares = new ArrayList<>();

		if (request.getDemandIds() != null) {
			for (Long dId : request.getDemandIds()) {
				if (dId == null)
					continue;

				Demand demand = demandRepo.findById(dId)
						.orElseThrow(() -> new IllegalArgumentException("Demand not found: " + dId));

				String dStatus = demand.getOverallStatus();
				if ("Hold".equalsIgnoreCase(dStatus) || "Rejected".equalsIgnoreCase(dStatus)) {
					throw new IllegalArgumentException("Demand '" + demand.getDemandtitle() + "' is in '" + dStatus
							+ "' status . Cannot share resume.Change status to Open to continue");
				}

				// DB-backed duplicate check: same candidate already shared to same demand
				if (rrRepo.existsByDemandIdAndCandidateId(dId, candidateId)) {
					throw new IllegalArgumentException(
							"Resume has already been shared for Demand: " + demand.getDemandtitle());
				}

				// JSONB history duplicate check (legacy)
				boolean alreadySharedLegacy = history.stream().anyMatch(h -> "DEMAND".equals(h.get("type"))
						&& h.get("demandId") instanceof Number && ((Number) h.get("demandId")).longValue() == dId
						&& h.get("candidateId") instanceof Number);
				if (alreadySharedLegacy) {
					throw new IllegalArgumentException(
							"Resume has already been shared for Demand: " + demand.getDemandtitle());
				}

				Company company = companyRepo.findById(demand.getCompanyId()).orElseThrow(
						() -> new IllegalArgumentException("Company not found for demand: " + demand.getDemandid()));

				String clientName = "Internal / No Account";
				Long clientId = null;

				if (demand.getAccountId() != null) {
					Account account = accountRepo.findById(demand.getAccountId()).orElse(null);
					if (account != null) {
						clientName = account.getAccountName();
						clientId = account.getAccountId();
					}
				}

				// Create a ResourceRequest record linked to this candidate + demand
				com.ris.rms.entity.ResourceRequest rr = new com.ris.rms.entity.ResourceRequest();
				rr.setDemandId(dId);
				rr.setCandidateId(candidateId);
				rr.setResourceType("EXTERNAL");
				rr.setRequesterUserId(request.getActionByUserId());
				rr.setStatus("Submitted");
				rr.setNumberOfResources(1);
				com.ris.rms.entity.ResourceRequest savedRr = rrRepo.save(rr);
				long createdRequestId = savedRr.getRequestId();

				String emailSentTo = "Not sent";
				if (shouldEmail && actionUser.getEmail() != null && !actionUser.getEmail().isBlank()) {
					try {
						emailService.sendResumeShareEmailAsync(actionUser.getEmail(), clientName, candidateName,
								demand.getProjectName(), company.getCompanyName(), resumeResource);
						emailSentTo = actionUser.getEmail();
					} catch (Exception mailEx) {
						emailSentTo = "Failed: " + mailEx.getMessage();
					}
				} else if (shouldEmail) {
					emailSentTo = "Action User has no Email";
				}

				if (shouldEmail) {
					demandService.updateDemandStatusOnResumeShare(demand.getDemandid());
				}

				Map<String, Object> info = new LinkedHashMap<>();
				info.put("type", "DEMAND");
				info.put("demandId", demand.getDemandid());
				info.put("demandTitle", demand.getDemandtitle());
				info.put("requestId", createdRequestId);
				info.put("candidateId", candidateId);
				info.put("resourceType", "EXTERNAL");
				info.put("projectName", demand.getProjectName());
				info.put("clientId", clientId);
				info.put("clientName", clientName);
				info.put("emailSentTo", emailSentTo);
				info.put("status", statusCode);
				info.put("sharedAt",
						ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
				newShares.add(info);
			}
		}

		if (request.getGroupIds() != null) {
			for (Long groupId : request.getGroupIds()) {
				if (groupId == null)
					continue;

				boolean alreadyShared = history.stream().anyMatch(h -> "GROUP".equals(h.get("type"))
						&& h.get("groupId") instanceof Number && ((Number) h.get("groupId")).longValue() == groupId);

				if (alreadyShared) {
					throw new IllegalArgumentException(
							"Resume has already been shared for Request Group ID: " + groupId);
				}

				ResourceRequestGroup group = resReqGroupRepo.findById(groupId)
						.orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

				Project project = projectRepo.findById(group.getProjectId())
						.orElseThrow(() -> new IllegalArgumentException("Project not found for group: " + groupId));

				if (project.getAccountId() == null) {
					throw new IllegalArgumentException(
							"Project " + project.getProjectId() + " is not linked to an Account.");
				}

				Account account = accountRepo.findById(project.getAccountId())
						.orElseThrow(() -> new IllegalArgumentException(
								"Client Account not found for project: " + project.getProjectId()));

				Company company = companyRepo.findById(project.getCompanyId()).orElseThrow(
						() -> new IllegalArgumentException("Company not found for project: " + project.getProjectId()));

				String emailSentTo = "Not sent (Rejected)";
				if (shouldEmail && account.getContactPersonEmail() != null
						&& !account.getContactPersonEmail().isBlank()) {

					try {
						emailService.sendResumeShareEmailAsync(account.getContactPersonEmail(),
								account.getAccountName(), candidateName, project.getProjectName(),
								company.getCompanyName(), resumeResource);
						emailSentTo = account.getContactPersonEmail();
					} catch (Exception mailEx) {
						emailSentTo = "Failed: " + mailEx.getMessage();
					}
				} else if (shouldEmail) {
					emailSentTo = "No Email on File";
				}

				Map<String, Object> info = new LinkedHashMap<>();
				info.put("type", "GROUP");
				info.put("groupId", groupId);
				info.put("projectId", project.getProjectId());
				info.put("projectName", project.getProjectName());
				info.put("clientId", account.getAccountId());
				info.put("clientName", account.getAccountName());
				info.put("emailSentTo", emailSentTo);
				info.put("status", statusCode);
				info.put("sharedAt",
						ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
				newShares.add(info);
			}
		}

		history.addAll(newShares);

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("actionByUserId", request.getActionByUserId());
		meta.put("actionByUserName", request.getActionByUserName());
		meta.put("actionAt", request.getActionAt() != null ? request.getActionAt().toString() : null);
		meta.put("sharedWith", history);

		String metaJson = OM.writeValueAsString(meta);

		document.setResumeShareStatus(statusCode);
		document.setResumeShareMeta(metaJson);
		candidateDocumentRepo.save(document);

		request.setSharedWith(history);
		return request;
	}

	@Override
	public CandidateDto create(CandidateDto dto, MultipartFile resume) throws Exception {
		if (dto.getCompanyId() == null) {
			throw new IllegalArgumentException("companyId is required for Candidate");
		}
		validateCompany(dto.getCompanyId());
		validateUniqueEmail(dto.getCompanyId(), dto.getEmail());

		Candidate entity = toEntity(dto);
		sanitizeCandidateForDb(entity);
		entity.setCandidateId(null);
		entity.setCreatedDt(null);
		entity.setUpdatedDt(null);

		Candidate saved = candidateRepo.save(entity);

		handleSkills(saved.getCandidateId(), dto);

		if (resume != null && !resume.isEmpty()) {
			validateResume(resume);
			storeCandidateResumeDocument(saved, resume, 1);
		}

		return buildOutputDto(saved);
	}

	@Override
	public CandidateDto update(Long id, CandidateDto dto, MultipartFile resume) throws Exception {
		Candidate existing = candidateRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + id));

		if (dto.getCompanyId() != null && !Objects.equals(dto.getCompanyId(), existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed for a candidate");
		}

		if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()
				&& !dto.getEmail().equalsIgnoreCase(existing.getEmail())) {

			validateUniqueEmail(existing.getCompanyId(), dto.getEmail());
			existing.setEmail(dto.getEmail());
		}

		if (dto.getFirstName() != null)
			existing.setFirstName(dto.getFirstName());
		if (dto.getLastName() != null)
			existing.setLastName(dto.getLastName());
		if (dto.getPhoneNumber() != null)
			existing.setPhoneNumber(dto.getPhoneNumber());
		if (dto.getLocation() != null)
			existing.setLocation(dto.getLocation());
		if (dto.getExperienceYears() != null)
			existing.setExperienceYears(dto.getExperienceYears());
		if (dto.getStatus() != null)
			existing.setStatus(dto.getStatus());
		if (dto.getGender() != null)
			existing.setGender(dto.getGender());
		if (dto.getDegrees() != null)
			existing.setDegrees(dto.getDegrees());
		if (dto.getSpecialization() != null)
			existing.setSpecialization(dto.getSpecialization());
		if (dto.getYearOfPassing() != null)
			existing.setYearOfPassing(dto.getYearOfPassing());
		if (dto.getProfileSummary() != null)
			existing.setProfileSummary(dto.getProfileSummary());
		if (dto.getTrainingSummary() != null)
			existing.setTrainingSummary(dto.getTrainingSummary());
		if (dto.getCertificationSummary() != null)
			existing.setCertificationSummary(dto.getCertificationSummary());
		if (dto.getSourceType() != null)
			existing.setSourceType(dto.getSourceType());
		if (dto.getSourceName() != null)
			existing.setSourceName(dto.getSourceName());
		if (dto.getCurrentCompany() != null)
			existing.setCurrentCompany(dto.getCurrentCompany());

		if (dto.getCurrentCtc() != null)
			existing.setCurrentCtc(dto.getCurrentCtc());

		if (dto.getExpectedCtc() != null)
			existing.setExpectedCtc(dto.getExpectedCtc());

		if (dto.getNoticePeriod() != null)
			existing.setNoticePeriod(dto.getNoticePeriod());

		if (dto.getPreferredLocation() != null)
			existing.setPreferredLocation(dto.getPreferredLocation());

		if (dto.getPersonalEmailId() != null)
			existing.setPersonalEmailId(dto.getPersonalEmailId());

		if (dto.getComments() != null)
			existing.setComments(dto.getComments());
		sanitizeCandidateForDb(existing);
		existing.setUpdatedDt(OffsetDateTime.now());

		Candidate saved = candidateRepo.save(existing);

		handleSkills(saved.getCandidateId(), dto);

		if (resume != null && !resume.isEmpty()) {
			validateResume(resume);

			final int oldVersion = candidateDocumentRepo.findPrimaryResume(saved.getCandidateId()).map(old -> {
				old.setIsPrimary(false);
				candidateDocumentRepo.save(old);
				return old.getVersion() != null ? old.getVersion() : 1;
			}).orElse(0);

			storeCandidateResumeDocument(saved, resume, oldVersion + 1);
		}

		return buildOutputDto(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public CandidateDto getById(Long id) {
		Candidate c = candidateRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Candidate not found: " + id));
		return buildOutputDto(c);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CandidateDto> list(Long companyId, String q, String status, String sourceType, Integer page,
			Integer size) {

		Sort sort = Sort.by(Sort.Direction.DESC, "candidateId");
		List<Candidate> base;

		if (companyId != null) {
			base = candidateRepo.findAllByCompanyId(companyId).stream()
					.sorted(Comparator.comparing(Candidate::getCandidateId).reversed()).collect(Collectors.toList());
		} else if (page != null && size != null && page >= 0 && size > 0) {
			base = candidateRepo.findAll(PageRequest.of(page, size, sort)).getContent();
		} else {
			base = candidateRepo.findAll(sort);
		}

		String ql = q != null ? q.toLowerCase(Locale.ROOT) : null;
		String sl = status != null ? status.toLowerCase(Locale.ROOT) : null;
		String st = sourceType != null ? sourceType.toLowerCase(Locale.ROOT) : null;

		List<Candidate> filtered = base.stream()
				.filter(c -> sl == null
						|| (c.getStatus() != null && c.getStatus().toLowerCase(Locale.ROOT).contains(sl)))
				.filter(c -> st == null
						|| (c.getSourceType() != null && c.getSourceType().toLowerCase(Locale.ROOT).contains(st)))
				.filter(c -> {
					if (ql == null || ql.isBlank())
						return true;
					return (c.getFirstName() != null && c.getFirstName().toLowerCase(Locale.ROOT).contains(ql))
							|| (c.getLastName() != null && c.getLastName().toLowerCase(Locale.ROOT).contains(ql))
							|| (c.getEmail() != null && c.getEmail().toLowerCase(Locale.ROOT).contains(ql))
							|| (c.getLocation() != null && c.getLocation().toLowerCase(Locale.ROOT).contains(ql))
							|| (c.getSourceName() != null && c.getSourceName().toLowerCase(Locale.ROOT).contains(ql));
				}).collect(Collectors.toList());

		if (page != null && size != null && page >= 0 && size > 0) {
			int start = page * size;
			if (start >= filtered.size()) {
				return List.of();
			}
			int end = Math.min(start + size, filtered.size());
			filtered = filtered.subList(start, end);
		}

		List<Long> candidateIds = filtered.stream().map(Candidate::getCandidateId).collect(Collectors.toList());

		Map<Long, List<Long>> skillIdsByCand = Collections.emptyMap();
		Map<Long, List<String>> skillNamesByCand = Collections.emptyMap();

		if (!candidateIds.isEmpty()) {
			List<CandidateSkill> allSkillLinks = candidateSkillRepo.findAll().stream()
					.filter(cs -> candidateIds.contains(cs.getCandidateId())).collect(Collectors.toList());

			skillIdsByCand = allSkillLinks.stream().collect(Collectors.groupingBy(CandidateSkill::getCandidateId,
					Collectors.mapping(CandidateSkill::getSkillId, Collectors.toList())));

			List<Long> allSkillIds = allSkillLinks.stream().map(CandidateSkill::getSkillId).distinct().toList();

			Map<Long, String> skillNameMap = allSkillIds.isEmpty() ? Map.of()
					: skillRepo.findAllById(allSkillIds).stream()
							.collect(Collectors.toMap(Skill::getSkillId, Skill::getSkillName));

			skillNamesByCand = skillIdsByCand.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e
					.getValue().stream().map(skillNameMap::get).filter(Objects::nonNull).collect(Collectors.toList())));
		}

		List<CandidateDto> result = new ArrayList<>();
		for (Candidate c : filtered) {
			List<Long> sIds = skillIdsByCand.getOrDefault(c.getCandidateId(), List.of());
			List<String> sNames = skillNamesByCand.getOrDefault(c.getCandidateId(), List.of());
			CandidateDto dto = toDto(c, sIds, sNames);
			fillResumeFields(dto);
			result.add(dto);
		}

		return result;
	}

	@Override
	public void delete(Long id) {
		if (!candidateRepo.existsById(id)) {
			throw new IllegalArgumentException("Candidate not found: " + id);
		}
		candidateRepo.deleteById(id);
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResultDto importCandidates(Long companyId, InputStream inputStream, String filename) throws Exception {
		ImportResultDto result = new ImportResultDto();
		List<CandidateDto> candidatesToCreate = new ArrayList<>();

		if (filename == null || filename.isBlank())
			throw new IllegalArgumentException("Filename is required to determine file type.");

		String lowerFilename = filename.toLowerCase();

		try {
			if (lowerFilename.endsWith(".csv")) {
				candidatesToCreate = parseCsv(companyId, inputStream, result);
			} else if (lowerFilename.endsWith(".xlsx")) {
				candidatesToCreate = parseExcel(companyId, inputStream, result);
			} else {
				throw new IllegalArgumentException("Unsupported file type. Please upload a .csv or .xlsx file.");
			}
		} catch (Exception e) {
			result.getErrors().add("Error parsing file: " + e.getMessage());
			result.setFailureCount(result.getTotalRows());
			return result;
		}

		for (CandidateDto dto : candidatesToCreate) {
			try {
				create(dto, null);
				result.setSuccessCount(result.getSuccessCount() + 1);
			} catch (Exception e) {
				result.setFailureCount(result.getFailureCount() + 1);
				String errorMsg = String.format("Error creating candidate '%s %s' (Email: %s): %s", dto.getFirstName(),
						dto.getLastName(), dto.getEmail(), e.getMessage());
				result.getErrors().add(errorMsg);
			}
		}
		return result;
	}

	private List<CandidateDto> parseCsv(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<CandidateDto> dtos = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
				CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader()
						.setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

			Map<String, Integer> headerMap = csvParser.getHeaderMap();
			validateHeaders(headerMap.keySet());

			int rowNum = 1;
			for (CSVRecord csvRecord : csvParser) {
				result.setTotalRows(result.getTotalRows() + 1);
				try {
					CandidateDto dto = mapRecordToDto(companyId, csvRecord::get, headerMap.keySet(), rowNum);
					dtos.add(dto);
				} catch (Exception e) {
					result.setFailureCount(result.getFailureCount() + 1);
					result.getErrors().add("Error parsing CSV row " + rowNum + ": " + e.getMessage());
				}
				rowNum++;
			}
		}
		return dtos;
	}

	private List<CandidateDto> parseExcel(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<CandidateDto> dtos = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			if (sheet == null)
				throw new IllegalArgumentException("Excel file is empty or has no sheets.");

			Iterator<Row> rowIterator = sheet.iterator();
			if (!rowIterator.hasNext())
				throw new IllegalArgumentException("Excel sheet is empty.");

			Row headerRow = rowIterator.next();
			Map<String, Integer> headerMap = new HashMap<>();
			DataFormatter dataFormatter = new DataFormatter();
			for (Cell cell : headerRow) {
				String headerText = dataFormatter.formatCellValue(cell).trim();
				if (!headerText.isEmpty())
					headerMap.put(headerText.toLowerCase(), cell.getColumnIndex());
			}
			validateHeaders(headerMap.keySet());

			int rowNum = 1;
			while (rowIterator.hasNext()) {
				Row currentRow = rowIterator.next();
				result.setTotalRows(result.getTotalRows() + 1);
				if (isRowEmpty(currentRow))
					continue;

				try {
					Function<String, String> cellValueProvider = headerName -> {
						Integer colIndex = headerMap.get(headerName.toLowerCase());
						if (colIndex == null)
							return null;
						Cell cell = currentRow.getCell(colIndex);
						return (cell == null) ? null : dataFormatter.formatCellValue(cell).trim();
					};
					CandidateDto dto = mapRecordToDto(companyId, cellValueProvider, headerMap.keySet(), rowNum);
					dtos.add(dto);
				} catch (Exception e) {
					result.setFailureCount(result.getFailureCount() + 1);
					result.getErrors().add("Error parsing Excel row " + (rowNum + 1) + ": " + e.getMessage());
				}
				rowNum++;
			}
		}
		return dtos;
	}

	private CandidateDto mapRecordToDto(Long companyId, Function<String, String> valueProvider,
			Set<String> availableHeaders, int rowNum) {

		CandidateDto dto = new CandidateDto();
		dto.setCompanyId(companyId);

		String name = valueProvider.apply("Name");
		if (name != null && !name.isBlank()) {
			String[] nameParts = name.trim().split("\\s+", 2);
			dto.setFirstName(nameParts[0]);
			dto.setLastName(nameParts.length > 1 ? nameParts[1] : "");
		} else {
			throw new IllegalArgumentException("Required column 'Name' is missing or empty.");
		}

		dto.setEmail(valueProvider.apply("Email"));
		if (dto.getEmail() == null || dto.getEmail().isBlank())
			throw new IllegalArgumentException("Required column 'Email' is missing or empty.");

		dto.setPhoneNumber(valueProvider.apply("PhoneNumber"));
		dto.setLocation(valueProvider.apply("Location"));
		dto.setStatus(valueProvider.apply("Status"));

		String skillsRaw = valueProvider.apply("Skills");
		if (skillsRaw != null && !skillsRaw.isBlank()) {
			dto.setSkillNames(Arrays.stream(skillsRaw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty())
					.collect(Collectors.toList()));
		}

		String experienceStr = valueProvider.apply("Experience");
		if (experienceStr != null && !experienceStr.isBlank()) {
			Matcher m = Pattern.compile("^(\\d+)").matcher(experienceStr.trim());
			if (m.find()) {
				try {
					dto.setExperienceYears(Integer.parseInt(m.group(1)));
				} catch (NumberFormatException ignored) {
				}
			}
		}

		dto.setSourceType(valueProvider.apply("SourceType"));
		if (dto.getSourceType() == null)
			dto.setSourceType("Imported");

		dto.setSourceName(valueProvider.apply("SourceName"));

		dto.setGender(valueProvider.apply("Gender"));
		dto.setDegrees(valueProvider.apply("Degrees"));
		dto.setSpecialization(valueProvider.apply("Specialization"));
		dto.setYearOfPassing(asInteger(valueProvider.apply("YearOfPassing")));
		dto.setProfileSummary(valueProvider.apply("ProfileSummary"));
		dto.setTrainingSummary(valueProvider.apply("TrainingSummary"));
		dto.setCertificationSummary(valueProvider.apply("CertificationSummary"));

		return dto;
	}

	private void validateHeaders(Set<String> presentHeaders) {
		List<String> requiredHeaders = List.of("name", "email");
		for (String required : requiredHeaders) {
			boolean found = presentHeaders.stream().anyMatch(required::equalsIgnoreCase);
			if (!found)
				throw new IllegalArgumentException("Missing required header column: " + required);
		}
	}

	private Integer asInteger(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			String cleaned = s.trim().replaceAll(",", "").replaceAll("\\.0*$", "");
			return Integer.parseInt(cleaned);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean isRowEmpty(Row row) {
		if (row == null || row.getLastCellNum() <= 0)
			return true;
		for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
			Cell cell = row.getCell(cellNum);
			if (cell != null && cell.getCellType() != CellType.BLANK)
				return false;
		}
		return true;
	}

	private void validateCompany(Long companyId) {
		companyRepo.findById(companyId)
				.orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));
	}

	private void validateUniqueEmail(Long companyId, String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email is required for Candidate");
		}
		if (candidateRepo.existsByCompanyIdAndEmailIgnoreCase(companyId, email)) {
			throw new IllegalArgumentException("Candidate email already exists for this company");
		}
	}

	private void handleSkills(Long candidateId, CandidateDto dto) {
		if (candidateId == null)
			return;

		if (dto.getSkillIds() != null && !dto.getSkillIds().isEmpty()) {
			linkSkillsByIds(candidateId, dto.getSkillIds());
		} else if (dto.getSkillNames() != null && !dto.getSkillNames().isEmpty()) {
			upsertSkillsForCandidate(candidateId, dto.getSkillNames());
		}
	}

	private void linkSkillsByIds(Long candidateId, List<Long> skillIds) {
		candidateSkillRepo.deleteAllByCandidateId(candidateId);
		if (skillIds == null)
			return;

		for (Long sid : skillIds) {
			if (sid == null)
				continue;
			if (!skillRepo.existsById(sid)) {
				throw new IllegalArgumentException("Skill not found: " + sid);
			}
			CandidateSkill link = new CandidateSkill();
			link.setCandidateId(candidateId);
			link.setSkillId(sid);
			candidateSkillRepo.save(link);
		}
	}

	private void upsertSkillsForCandidate(Long candidateId, List<String> skillNamesIn) {
		List<String> normalized = skillNamesIn.stream().filter(Objects::nonNull).map(String::trim)
				.filter(s -> !s.isBlank()).collect(Collectors.toList());

		candidateSkillRepo.deleteAllByCandidateId(candidateId);

		for (String name : normalized) {
			Long skillId = skillRepo.findBySkillNameIgnoreCase(name).map(Skill::getSkillId).orElseGet(() -> {
				Skill s = new Skill();
				s.setSkillName(name);
				return skillRepo.save(s).getSkillId();
			});

			CandidateSkill cs = new CandidateSkill();
			cs.setCandidateId(candidateId);
			cs.setSkillId(skillId);
			candidateSkillRepo.save(cs);
		}
	}

	private CandidateDto buildOutputDto(Candidate c) {
		List<Long> skillIds = candidateSkillRepo.findAllByCandidateId(c.getCandidateId()).stream()
				.map(CandidateSkill::getSkillId).toList();

		List<String> skillNames = skillIds.isEmpty() ? List.of()
				: skillRepo.findAllById(skillIds).stream().map(Skill::getSkillName).filter(Objects::nonNull).toList();

		CandidateDto dto = toDto(c, skillIds, skillNames);
		fillResumeFields(dto);
		return dto;
	}

	private CandidateDto toDto(Candidate c, List<Long> skillIds, List<String> skills) {
		CandidateDto dto = new CandidateDto();
		dto.setCandidateId(c.getCandidateId());
		dto.setCompanyId(c.getCompanyId());
		String companyName = companyRepo.findById(c.getCompanyId()).map(Company::getCompanyName).orElse(null);
		dto.setCompanyName(companyName);

		dto.setFirstName(c.getFirstName());
		dto.setLastName(c.getLastName());
		dto.setFullName(c.getFullName());
		dto.setEmail(c.getEmail());
		dto.setPhoneNumber(c.getPhoneNumber());
		dto.setLocation(c.getLocation());
		dto.setExperienceYears(c.getExperienceYears());
		dto.setStatus(c.getStatus());
		dto.setGender(c.getGender());
		dto.setDegrees(c.getDegrees());
		dto.setSpecialization(c.getSpecialization());
		dto.setYearOfPassing(c.getYearOfPassing());
		dto.setProfileSummary(c.getProfileSummary());
		dto.setTrainingSummary(c.getTrainingSummary());
		dto.setCertificationSummary(c.getCertificationSummary());
		dto.setSourceType(c.getSourceType());
		dto.setSourceName(c.getSourceName());
		dto.setSkillIds(skillIds);
		dto.setSkillNames(skills);
		dto.setPrimarySkills(skills == null ? List.of() : skills);
		dto.setSecondarySkills(List.of());
		dto.setCreatedAt(c.getCreatedDt());
		dto.setUpdatedAt(c.getUpdatedDt());

		dto.setCurrentCompany(c.getCurrentCompany());
		dto.setCurrentCtc(c.getCurrentCtc());
		dto.setExpectedCtc(c.getExpectedCtc());
		dto.setNoticePeriod(c.getNoticePeriod());
		dto.setPreferredLocation(c.getPreferredLocation());
		dto.setPersonalEmailId(c.getPersonalEmailId());
		dto.setComments(c.getComments());
		return dto;
	}

	private Candidate toEntity(CandidateDto dto) {
		Candidate c = new Candidate();
		c.setCandidateId(dto.getCandidateId());
		c.setCompanyId(dto.getCompanyId());
		c.setFirstName(dto.getFirstName());
		c.setLastName(dto.getLastName());
		c.setEmail(dto.getEmail());
		c.setPhoneNumber(dto.getPhoneNumber());
		c.setLocation(dto.getLocation());
		c.setExperienceYears(dto.getExperienceYears());
		c.setStatus(dto.getStatus());
		c.setGender(dto.getGender());
		c.setDegrees(dto.getDegrees());
		c.setSpecialization(dto.getSpecialization());
		c.setYearOfPassing(dto.getYearOfPassing());
		c.setProfileSummary(dto.getProfileSummary());
		c.setTrainingSummary(dto.getTrainingSummary());
		c.setCertificationSummary(dto.getCertificationSummary());
		c.setSourceType(dto.getSourceType());
		c.setSourceName(dto.getSourceName());
		c.setCurrentCompany(dto.getCurrentCompany());
		c.setCurrentCtc(dto.getCurrentCtc());
		c.setExpectedCtc(dto.getExpectedCtc());
		c.setNoticePeriod(dto.getNoticePeriod());
		c.setPreferredLocation(dto.getPreferredLocation());
		c.setPersonalEmailId(dto.getPersonalEmailId());
		c.setComments(dto.getComments());
		return c;
	}

	private void sanitizeCandidateForDb(Candidate c) {
		c.setFirstName(trimTo(c.getFirstName(), 255));
		c.setLastName(trimTo(c.getLastName(), 100));
		c.setEmail(trimTo(c.getEmail(), 255));
		c.setPhoneNumber(trimTo(c.getPhoneNumber(), 13));
		c.setLocation(trimTo(c.getLocation(), 255));
		c.setStatus(trimTo(c.getStatus(), 50));
		c.setGender(trimTo(c.getGender(), 20));
		c.setDegrees(trimTo(c.getDegrees(), 255));
		c.setSpecialization(trimTo(c.getSpecialization(), 255));
		c.setProfileSummary(trimTo(c.getProfileSummary(), 255));
		c.setTrainingSummary(trimTo(c.getTrainingSummary(), 255));
		c.setCertificationSummary(trimTo(c.getCertificationSummary(), 255));
		c.setSourceType(trimTo(c.getSourceType(), 255));
		c.setSourceName(trimTo(c.getSourceName(), 255));
		c.setCurrentCompany(trimTo(c.getCurrentCompany(), 255));
		c.setNoticePeriod(trimTo(c.getNoticePeriod(), 255));
		c.setPreferredLocation(trimTo(c.getPreferredLocation(), 255));
		c.setPersonalEmailId(trimTo(c.getPersonalEmailId(), 255));
	}

	private String trimTo(String value, int maxLen) {
		if (value == null)
			return null;
		String v = value.trim();
		if (v.length() <= maxLen)
			return v;
		return v.substring(0, maxLen);
	}

	private void validateResume(MultipartFile f) {
		if (f.getSize() > MAX_RESUME_BYTES) {
			throw new IllegalArgumentException("Resume exceeds 10 MB");
		}

		String name = Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
		if (!(name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx"))) {
			throw new IllegalArgumentException("Unsupported resume file type (allowed: PDF, DOC, DOCX)");
		}
	}

	private void storeCandidateResumeDocument(Candidate candidate, MultipartFile resume, int version) throws Exception {
		String origName = resume.getOriginalFilename();
		String lower = (origName == null ? "" : origName.toLowerCase(Locale.ROOT));
		String contentType = resume.getContentType();

		boolean isPdf = "application/pdf".equalsIgnoreCase(contentType) || lower.endsWith(".pdf");
		boolean isDocx = lower.endsWith(".docx")
				|| "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
						.equalsIgnoreCase(contentType);
		boolean isDoc = lower.endsWith(".doc") || "application/msword".equalsIgnoreCase(contentType);

		if (isPdf) {
			var stored = storage.upload(candidate.getCandidateId(), origName, "application/pdf",
					resume.getInputStream(), resume.getSize());

			CandidateDocument doc = new CandidateDocument();
			doc.setCandidateId(candidate.getCandidateId());
			doc.setDocumentName(stored.fileName());
			doc.setFilePath(stored.url());
			doc.setDocumentType("resume");
			doc.setMimeType("application/pdf");
			doc.setSizeBytes(stored.sizeBytes());
			doc.setStorageProvider(stored.storageProvider());
			doc.setStorageKey(stored.key());
			doc.setIsPrimary(true);
			doc.setVersion(version);
			doc.setResumeShareMeta(null);
			doc.setResumeShareStatus(null);
			candidateDocumentRepo.save(doc);

		} else if (isDoc || isDocx) {
			IConverter converter = null;
			try (InputStream in = resume.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				try {
					converter = LocalConverter.builder().build();
					DocumentType inType = isDocx ? DocumentType.DOCX : DocumentType.DOC;

					boolean ok = converter.convert(in).as(inType).to(out).as(DocumentType.PDF).prioritizeWith(1000)
							.schedule().get();
					if (!ok) {
						throw new IllegalStateException("Resume conversion to PDF failed");
					}

					byte[] pdfBytes = out.toByteArray();
					String pdfName = replaceExt(origName, ".pdf");
					var storedPdf = storage.uploadBytes(candidate.getCandidateId(), pdfName, "application/pdf",
							pdfBytes);

					CandidateDocument doc = new CandidateDocument();
					doc.setCandidateId(candidate.getCandidateId());
					doc.setDocumentName(storedPdf.fileName());
					doc.setFilePath(storedPdf.url());
					doc.setDocumentType("resume");
					doc.setMimeType("application/pdf");
					doc.setSizeBytes(storedPdf.sizeBytes());
					doc.setStorageProvider(storedPdf.storageProvider());
					doc.setStorageKey(storedPdf.key());
					doc.setIsPrimary(true);
					doc.setVersion(version);
					doc.setResumeShareMeta(null);
					doc.setResumeShareStatus(null);
					candidateDocumentRepo.save(doc);
				} catch (Exception ex) {
					log.warn(
							"DOC/DOCX to PDF conversion failed for candidate {}. Storing original resume instead. Cause: {}",
							candidate.getCandidateId(), ex.getMessage());
					String fallbackMime = (contentType == null || contentType.isBlank())
							? (isDocx ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
									: "application/msword")
							: contentType;
					var storedOriginal = storage.upload(candidate.getCandidateId(), origName, fallbackMime,
							resume.getInputStream(), resume.getSize());

					CandidateDocument doc = new CandidateDocument();
					doc.setCandidateId(candidate.getCandidateId());
					doc.setDocumentName(storedOriginal.fileName());
					doc.setFilePath(storedOriginal.url());
					doc.setDocumentType("resume");
					doc.setMimeType(fallbackMime);
					doc.setSizeBytes(storedOriginal.sizeBytes());
					doc.setStorageProvider(storedOriginal.storageProvider());
					doc.setStorageKey(storedOriginal.key());
					doc.setIsPrimary(true);
					doc.setVersion(version);
					doc.setResumeShareMeta(null);
					doc.setResumeShareStatus(null);
					candidateDocumentRepo.save(doc);
				}
			} finally {
				if (converter != null) {
					try {
						converter.shutDown();
					} catch (Exception ignore) {
					}
				}
			}
		} else {
			throw new IllegalArgumentException("Unsupported resume file type (expected PDF/DOC/DOCX)");
		}
	}

	private void fillResumeFields(CandidateDto dto) {
		CandidateDocument primary = candidateDocumentRepo.findPrimaryResume(dto.getCandidateId()).orElse(null);

		if (primary == null) {
			dto.setResumeShareAudit(List.of());
			return;
		}

		dto.setResumeUrl(primary.getFilePath());
		dto.setResumeFileName(primary.getDocumentName());
		dto.setResumeMimeType(primary.getMimeType());
		dto.setResumeDocumentId(primary.getDocumentId());
		dto.setStorageType(primary.getStorageProvider());
		dto.setResumeStatus(primary.getResumeShareStatus());
		if (primary.getUploadedAt() != null) {
			dto.setResumeUploadedAt(primary.getUploadedAt().toString());
		}

		if (primary.getResumeShareMeta() != null && !primary.getResumeShareMeta().isBlank()) {
			try {
				var node = OM.readTree(primary.getResumeShareMeta());
				if (node.hasNonNull("actionByUserId")) {
					dto.setResumeShareActionByUserId(node.get("actionByUserId").asLong());
				}
				if (node.hasNonNull("actionByUserName")) {
					dto.setResumeShareActionByUserName(node.get("actionByUserName").asText());
				}
				if (node.hasNonNull("actionAt")) {
					dto.setResumeShareActionAt(node.get("actionAt").asText());
				}

				if (node.has("sharedWith") && node.get("sharedWith").isArray()) {
					List<Map<String, Object>> list = OM.convertValue(node.get("sharedWith"),
							new TypeReference<List<Map<String, Object>>>() {
							});
					dto.setResumeShareAudit(list);
				} else {
					dto.setResumeShareAudit(List.of());
				}
			} catch (Exception ignore) {
				dto.setResumeShareAudit(List.of());
			}
		} else {
			dto.setResumeShareAudit(List.of());
		}
	}

	private static String replaceExt(String name, String ext) {
		if (name == null || name.isBlank()) {
			return "resume" + ext;
		}
		int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name) + ext;
	}
}

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
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import com.ris.rms.dto.EmployeeDocumentDto;
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
import com.ris.rms.service.ImportResumeResolver;
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

	@Autowired
	@Lazy
	private CandidateService self;
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
		} catch (Exception ex) {
			log.warn("Resume preview conversion failed for candidate {}. Serving original file instead. Cause: {}",
					candidateId, ex.getMessage());
			return original;
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

		if ("Inactive".equalsIgnoreCase(candidate.getStatus())) {
			throw new IllegalArgumentException("Cannot share resume: Resource " + candidate.getFirstName() + " " + candidate.getLastName() + " is marked as Inactive.");
		}

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

				if (shouldEmail) {
					String dStatus = demand.getOverallStatus();
					if ("Hold".equalsIgnoreCase(dStatus) || "Rejected".equalsIgnoreCase(dStatus)) {
						throw new IllegalArgumentException("Demand '" + demand.getDemandtitle() + "' is in '" + dStatus
								+ "' status . Cannot share resume.Change status to Open to continue");
					}

					// DB-backed duplicate check: same candidate already actively shared to same demand
					List<com.ris.rms.entity.ResourceRequest> existingReqs = rrRepo.findAllByDemandId(dId).stream()
							.filter(r -> Objects.equals(r.getCandidateId(), candidateId))
							.toList();
					boolean alreadyActive = existingReqs.stream().anyMatch(r -> !"Rejected".equalsIgnoreCase(r.getStatus()));
					if (alreadyActive) {
						throw new IllegalArgumentException(
								"Resume has already been shared for Demand: " + demand.getDemandtitle());
					}

					// JSONB history duplicate check (legacy)
					boolean alreadySharedLegacy = history.stream().anyMatch(h -> "DEMAND".equals(h.get("type"))
							&& h.get("demandId") instanceof Number && ((Number) h.get("demandId")).longValue() == dId
							&& "Shared".equalsIgnoreCase(String.valueOf(h.get("status"))));
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

					// Create or reactivate ResourceRequest record linked to this candidate + demand
					long createdRequestId;
					if (!existingReqs.isEmpty()) {
						com.ris.rms.entity.ResourceRequest rr = existingReqs.get(0);
						rr.setStatus("Submitted");
						rr.setRequesterUserId(request.getActionByUserId());
						com.ris.rms.entity.ResourceRequest savedRr = rrRepo.save(rr);
						createdRequestId = savedRr.getRequestId();
					} else {
						com.ris.rms.entity.ResourceRequest rr = new com.ris.rms.entity.ResourceRequest();
						rr.setDemandId(dId);
						rr.setCandidateId(candidateId);
						rr.setResourceType("EXTERNAL");
						rr.setRequesterUserId(request.getActionByUserId());
						rr.setStatus("Submitted");
						rr.setNumberOfResources(1);
						com.ris.rms.entity.ResourceRequest savedRr = rrRepo.save(rr);
						createdRequestId = savedRr.getRequestId();
					}

					String emailSentTo = "Not sent";
					if (actionUser.getEmail() != null && !actionUser.getEmail().isBlank()) {
						try {
							emailService.sendResumeShareEmailAsync(actionUser.getEmail(), clientName, candidateName,
									demand.getProjectName(), company.getCompanyName(), resumeResource);
							emailSentTo = actionUser.getEmail();
						} catch (Exception mailEx) {
							emailSentTo = "Failed: " + mailEx.getMessage();
						}
					} else {
						emailSentTo = "Action User has no Email";
					}

					demandService.updateDemandStatusOnResumeShare(demand.getDemandid());

					boolean updatedInHistory = false;
					for (Map<String, Object> h : history) {
						if ("DEMAND".equals(h.get("type"))
								&& h.get("demandId") instanceof Number
								&& ((Number) h.get("demandId")).longValue() == dId) {
							h.put("status", "Shared");
							h.put("sharedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
							h.put("requestId", createdRequestId);
							h.put("emailSentTo", emailSentTo);
							h.put("clientId", clientId);
							h.put("clientName", clientName);
							h.put("projectName", demand.getProjectName());
							updatedInHistory = true;
							break;
						}
					}
					if (!updatedInHistory) {
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
						info.put("status", "Shared");
						info.put("sharedAt",
								ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
						newShares.add(info);
					}
				} else {
					// REJECT: Update existing ResourceRequest(s) to "Rejected"
					List<com.ris.rms.entity.ResourceRequest> existingReqs = rrRepo.findAllByDemandId(dId).stream()
							.filter(r -> Objects.equals(r.getCandidateId(), candidateId))
							.toList();
					for (com.ris.rms.entity.ResourceRequest req : existingReqs) {
						req.setStatus("Rejected");
						rrRepo.save(req);
					}

					boolean updatedInHistory = false;
					for (Map<String, Object> h : history) {
						if ("DEMAND".equals(h.get("type"))
								&& h.get("demandId") instanceof Number
								&& ((Number) h.get("demandId")).longValue() == dId) {
							h.put("status", "Rejected");
							h.put("rejectedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
							h.put("actionByUserId", request.getActionByUserId());
							h.put("actionByUserName", request.getActionByUserName());
							updatedInHistory = true;
							break;
						}
					}
					if (!updatedInHistory) {
						Map<String, Object> info = new LinkedHashMap<>();
						info.put("type", "DEMAND");
						info.put("demandId", demand.getDemandid());
						info.put("demandTitle", demand.getDemandtitle());
						info.put("candidateId", candidateId);
						info.put("resourceType", "EXTERNAL");
						info.put("projectName", demand.getProjectName());
						info.put("status", "Rejected");
						info.put("rejectedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
						info.put("actionByUserId", request.getActionByUserId());
						info.put("actionByUserName", request.getActionByUserName());
						newShares.add(info);
					}
				}
			}
		}

		if (request.getGroupIds() != null) {
			for (Long groupId : request.getGroupIds()) {
				if (groupId == null)
					continue;

				if (shouldEmail) {
					boolean alreadyShared = history.stream().anyMatch(h -> "GROUP".equals(h.get("type"))
							&& h.get("groupId") instanceof Number && ((Number) h.get("groupId")).longValue() == groupId
							&& "Shared".equalsIgnoreCase(String.valueOf(h.get("status"))));

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

					String emailSentTo = "Not sent";
					if (account.getContactPersonEmail() != null
							&& !account.getContactPersonEmail().isBlank()) {

						try {
							emailService.sendResumeShareEmailAsync(account.getContactPersonEmail(),
									account.getAccountName(), candidateName, project.getProjectName(),
									company.getCompanyName(), resumeResource);
							emailSentTo = account.getContactPersonEmail();
						} catch (Exception mailEx) {
							emailSentTo = "Failed: " + mailEx.getMessage();
						}
					} else {
						emailSentTo = "No Email on File";
					}

					boolean updatedInHistory = false;
					for (Map<String, Object> h : history) {
						if ("GROUP".equals(h.get("type"))
								&& h.get("groupId") instanceof Number
								&& ((Number) h.get("groupId")).longValue() == groupId) {
							h.put("status", "Shared");
							h.put("sharedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
							h.put("emailSentTo", emailSentTo);
							h.put("clientId", account.getAccountId());
							h.put("clientName", account.getAccountName());
							h.put("projectId", project.getProjectId());
							h.put("projectName", project.getProjectName());
							updatedInHistory = true;
							break;
						}
					}
					if (!updatedInHistory) {
						Map<String, Object> info = new LinkedHashMap<>();
						info.put("type", "GROUP");
						info.put("groupId", groupId);
						info.put("projectId", project.getProjectId());
						info.put("projectName", project.getProjectName());
						info.put("clientId", account.getAccountId());
						info.put("clientName", account.getAccountName());
						info.put("emailSentTo", emailSentTo);
						info.put("status", "Shared");
						info.put("sharedAt",
								ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
						newShares.add(info);
					}
				} else {
					// REJECT / WITHDRAW for group
					boolean updatedInHistory = false;
					for (Map<String, Object> h : history) {
						if ("GROUP".equals(h.get("type"))
								&& h.get("groupId") instanceof Number
								&& ((Number) h.get("groupId")).longValue() == groupId) {
							h.put("status", "Rejected");
							h.put("rejectedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
							h.put("actionByUserId", request.getActionByUserId());
							h.put("actionByUserName", request.getActionByUserName());
							updatedInHistory = true;
							break;
						}
					}
					if (!updatedInHistory) {
						ResourceRequestGroup group = resReqGroupRepo.findById(groupId).orElse(null);
						Map<String, Object> info = new LinkedHashMap<>();
						info.put("type", "GROUP");
						info.put("groupId", groupId);
						if (group != null) {
							info.put("projectId", group.getProjectId());
						}
						info.put("status", "Rejected");
						info.put("rejectedAt", ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime().toString());
						info.put("actionByUserId", request.getActionByUserId());
						info.put("actionByUserName", request.getActionByUserName());
						newShares.add(info);
					}
				}
			}
		}

		history.addAll(newShares);

		boolean hasAnyActiveShare = history.stream().anyMatch(h -> "Shared".equalsIgnoreCase(String.valueOf(h.get("status"))));
		String effectiveDocStatus = hasAnyActiveShare ? "Shared" : "Rejected";

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("actionByUserId", request.getActionByUserId());
		meta.put("actionByUserName", request.getActionByUserName());
		meta.put("actionAt", request.getActionAt() != null ? request.getActionAt().toString() : null);
		meta.put("sharedWith", history);

		String metaJson = OM.writeValueAsString(meta);

		document.setResumeShareStatus(effectiveDocStatus);
		document.setResumeShareMeta(metaJson);
		candidateDocumentRepo.save(document);

		request.setStatus(effectiveDocStatus);
		request.setStatusSet(effectiveDocStatus);
		request.setSharedWith(history);
		return request;
	}

	@Override
	public ResumeShareDto deleteShare(Long candidateId, Long demandId, Long groupId, String type) throws Exception {
		if (candidateId == null) {
			throw new IllegalArgumentException("Candidate ID is required.");
		}

		CandidateDocument document = candidateDocumentRepo.findPrimaryResume(candidateId)
				.orElseThrow(() -> new IllegalArgumentException("No resume available for this candidate."));

		List<Map<String, Object>> history = new ArrayList<>();
		if (document.getResumeShareMeta() != null && !document.getResumeShareMeta().isBlank()) {
			try {
				Map<String, Object> oldMeta = OM.readValue(document.getResumeShareMeta(),
						new TypeReference<Map<String, Object>>() {});
				if (oldMeta.containsKey("sharedWith") && oldMeta.get("sharedWith") instanceof List) {
					history = (List<Map<String, Object>>) oldMeta.get("sharedWith");
				}
			} catch (Exception e) {}
		}

		// Find target item
		Map<String, Object> targetItem = null;
		for (Map<String, Object> h : history) {
			if ("opportunity".equalsIgnoreCase(type) || "group".equalsIgnoreCase(type)) {
				if ("GROUP".equals(h.get("type")) && h.get("groupId") instanceof Number
						&& ((Number) h.get("groupId")).longValue() == groupId) {
					targetItem = h;
					break;
				}
			} else {
				if ("DEMAND".equals(h.get("type")) && h.get("demandId") instanceof Number
						&& ((Number) h.get("demandId")).longValue() == demandId) {
					targetItem = h;
					break;
				}
			}
		}

		if (targetItem == null) {
			throw new IllegalArgumentException("No share record found for the specified "
					+ ("opportunity".equalsIgnoreCase(type) ? "opportunity" : "demand") + ".");
		}

		String currentStatus = String.valueOf(targetItem.get("status"));
		if (!"Rejected".equalsIgnoreCase(currentStatus)) {
			throw new IllegalArgumentException("Only rejected demands can be deleted. Please reject the demand first before deleting.");
		}

		history.remove(targetItem);

		// Delete corresponding ResourceRequest records
		if (demandId != null) {
			List<com.ris.rms.entity.ResourceRequest> reqs = rrRepo.findAllByDemandId(demandId).stream()
					.filter(r -> Objects.equals(r.getCandidateId(), candidateId))
					.toList();
			rrRepo.deleteAll(reqs);
		}

		boolean hasAnyActiveShare = history.stream().anyMatch(h -> "Shared".equalsIgnoreCase(String.valueOf(h.get("status"))));
		boolean hasAnyRejectedShare = history.stream().anyMatch(h -> "Rejected".equalsIgnoreCase(String.valueOf(h.get("status"))));
		String effectiveDocStatus = hasAnyActiveShare ? "Shared" : (hasAnyRejectedShare ? "Rejected" : null);

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("sharedWith", history);
		String metaJson = OM.writeValueAsString(meta);

		document.setResumeShareStatus(effectiveDocStatus);
		document.setResumeShareMeta(metaJson);
		candidateDocumentRepo.save(document);

		ResumeShareDto result = new ResumeShareDto();
		result.setCandidateId(candidateId);
		result.setStatus(effectiveDocStatus);
		result.setSharedWith(history);
		return result;
	}

	@Override
	public CandidateDto create(CandidateDto dto, MultipartFile resume, List<MultipartFile> documentFiles, String documentData) throws Exception {
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
		storeAdditionalCandidateDocuments(saved, documentFiles, documentData);

		return buildOutputDto(saved);
	}

	@Override
	public CandidateDto update(Long id, CandidateDto dto, MultipartFile resume, List<MultipartFile> documentFiles, String documentData) throws Exception {
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

		// CORRECT WAY to handle update/clear:
		if (dto.getPersonalEmailId() != null) {
		    String newPersonalEmail = dto.getPersonalEmailId().isBlank() ? null : dto.getPersonalEmailId().trim();
		    existing.setPersonalEmailId(newPersonalEmail);
		}

		if (dto.getComments() != null)
			existing.setComments(dto.getComments());

		// New fields
		if (dto.getMiddleName() != null) existing.setMiddleName(dto.getMiddleName());
		if (dto.getDateOfBirth() != null) existing.setDateOfBirth(dto.getDateOfBirth());
		if (dto.getPrimaryCountryCode() != null) existing.setPrimaryCountryCode(dto.getPrimaryCountryCode());
		if (dto.getPrimaryContactNo() != null) existing.setPrimaryContactNo(dto.getPrimaryContactNo());
		if (dto.getSecondaryCountryCode() != null) existing.setSecondaryCountryCode(dto.getSecondaryCountryCode());
		if (dto.getSecondaryContactNo() != null) existing.setSecondaryContactNo(dto.getSecondaryContactNo());
		if (dto.getCountryOfCitizenship() != null) existing.setCountryOfCitizenship(dto.getCountryOfCitizenship());
		if (dto.getDocumentType() != null) existing.setDocumentType(dto.getDocumentType());
		if (dto.getDocumentNumber() != null) existing.setDocumentNumber(dto.getDocumentNumber());
		if (dto.getSecurityClearance() != null) existing.setSecurityClearance(dto.getSecurityClearance());
		if (dto.getVisa() != null) existing.setVisa(dto.getVisa());
		if (dto.getVisaType() != null) existing.setVisaType(dto.getVisaType());
		if (dto.getCountry() != null) existing.setCountry(dto.getCountry());
		if (dto.getState() != null) existing.setState(dto.getState());
		if (dto.getCity() != null) existing.setCity(dto.getCity());
		if (dto.getZipCode() != null) existing.setZipCode(dto.getZipCode());
		if (dto.getStreet() != null) existing.setStreet(dto.getStreet());
		if (dto.getAvailabilityToJoin() != null) existing.setAvailabilityToJoin(dto.getAvailabilityToJoin());
		if (dto.getInterviewAvailability() != null) existing.setInterviewAvailability(dto.getInterviewAvailability());
		if (dto.getHighestQualification() != null) existing.setHighestQualification(dto.getHighestQualification());
		if (dto.getUniversityName() != null) existing.setUniversityName(dto.getUniversityName());
		if (dto.getDateOfQualification() != null) existing.setDateOfQualification(dto.getDateOfQualification());
		if (dto.getUsaDegree() != null) existing.setUsaDegree(dto.getUsaDegree());
		if (dto.getCurrentJobTitle() != null) existing.setCurrentJobTitle(dto.getCurrentJobTitle());
		if (dto.getMostRecentEmployer() != null) existing.setMostRecentEmployer(dto.getMostRecentEmployer());
		if (dto.getTotalExperience() != null) existing.setTotalExperience(dto.getTotalExperience());
		if (dto.getRelocate() != null) existing.setRelocate(dto.getRelocate());
		if (dto.getCurrency() != null) existing.setCurrency(dto.getCurrency());
		if (dto.getFrequency() != null) existing.setFrequency(dto.getFrequency());
		if (dto.getSourcingRate() != null) existing.setSourcingRate(dto.getSourcingRate());
		if (dto.getResumeSummary() != null) existing.setResumeSummary(dto.getResumeSummary());
		if (dto.getSuggestedKeywords() != null) existing.setSuggestedKeywords(dto.getSuggestedKeywords());
		if (dto.getSocialLinks() != null) existing.setSocialLinksJson(dto.getSocialLinks());
		if (dto.getCurrentAccountId() != null) existing.setCurrentAccountId(dto.getCurrentAccountId());

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
		storeAdditionalCandidateDocuments(saved, documentFiles, documentData);

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
			List<CandidateSkill> allSkillLinks = candidateSkillRepo.findAllByCandidateIdIn(candidateIds);
			
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

	private record ImportedCandidateRow(CandidateDto dto, String resumePath, int rowNum) {
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResultDto importCandidates(Long companyId, InputStream inputStream, String filename) throws Exception {
		return importCandidates(companyId, inputStream, filename, false);
	}

	@Override
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResultDto importCandidates(Long companyId, InputStream inputStream, String filename,
			boolean overrideExisting) throws Exception {
		ImportResultDto result = new ImportResultDto();
		List<ImportedCandidateRow> rowsToImport = new ArrayList<>();

		if (filename == null || filename.isBlank())
			throw new IllegalArgumentException("Filename is required to determine file type.");

		String lowerFilename = filename.toLowerCase(Locale.ROOT);

		try {
			if (lowerFilename.endsWith(".csv")) {
				rowsToImport = parseCsv(companyId, inputStream, result);
			} else if (lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls")) {
				rowsToImport = parseExcel(companyId, inputStream, result);
			} else {
				throw new IllegalArgumentException(
						"Unsupported file type. Please upload a .csv, .xls, or .xlsx file.");
			}
		} catch (Exception e) {
			result.getErrors().add("Error parsing file: " + e.getMessage());
			result.setFailureCount(Math.max(result.getFailureCount(), result.getTotalRows()));
			return result;
		}

		for (ImportedCandidateRow row : rowsToImport) {
			CandidateDto dto = row.dto();
			try {
				Optional<Candidate> existingOpt = candidateRepo.findByCompanyIdAndEmailIgnoreCase(companyId,
						dto.getEmail());
				if (existingOpt.isEmpty() && overrideExisting) {
					existingOpt = ImportResumeResolver.matchByPhoneAndOptionalName(
							candidateRepo.findAllByCompanyId(companyId),
							dto.getPhoneNumber(),
							dto.getFirstName(),
							dto.getLastName(),
							cand -> List.of(cand.getPhoneNumber() == null ? "" : cand.getPhoneNumber()),
							cand -> ImportResumeResolver.normalizeFullNameKey(cand.getFirstName(), cand.getLastName()));
				}
				if (existingOpt.isPresent()) {
					if (!overrideExisting) {
						throw new IllegalArgumentException("Candidate email '" + dto.getEmail()
								+ "' already exists for this company (enable Override existing to update).");
					}
					Candidate existing = existingOpt.get();
					MultipartFile resumeFile = null;
					if (row.resumePath() != null && !row.resumePath().isBlank()) {
						resumeFile = ImportResumeResolver.resolve(row.resumePath());
					} else if (candidateDocumentRepo.findPrimaryResume(existing.getCandidateId()).isEmpty()) {
						throw new IllegalArgumentException(
								"Row " + row.rowNum() + ": Required column 'Resume Path' is missing or empty.");
					}
					self.update(existing.getCandidateId(), dto, resumeFile, null, null);
					result.setUpdatedCount(result.getUpdatedCount() + 1);
					result.setSuccessCount(result.getSuccessCount() + 1);
				} else {
					if (row.resumePath() == null || row.resumePath().isBlank()) {
						throw new IllegalArgumentException(
								"Row " + row.rowNum() + ": Required column 'Resume Path' is missing or empty.");
					}
					MultipartFile resumeFile = ImportResumeResolver.resolve(row.resumePath());
					if (dto.getSourceType() == null || dto.getSourceType().isBlank()) {
						dto.setSourceType("Imported");
					}
					self.create(dto, resumeFile, null, null);
					result.setCreatedCount(result.getCreatedCount() + 1);
					result.setSuccessCount(result.getSuccessCount() + 1);
				}
			} catch (Exception e) {
				result.setFailureCount(result.getFailureCount() + 1);
				String errorMsg = String.format("Row %d ('%s %s', Email: %s): %s", row.rowNum(),
						dto.getFirstName() != null ? dto.getFirstName() : "",
						dto.getLastName() != null ? dto.getLastName() : "",
						dto.getEmail(), e.getMessage());
				result.getErrors().add(errorMsg);
			}
		}
		return result;
	}

	private static final Pattern BASIC_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private List<ImportedCandidateRow> parseCsv(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<ImportedCandidateRow> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
				CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader()
						.setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

			Map<String, Integer> rawHeaderMap = csvParser.getHeaderMap();
			Map<String, Integer> normalizedHeaderMap = new HashMap<>();
			for (Map.Entry<String, Integer> entry : rawHeaderMap.entrySet()) {
				String norm = ImportResumeResolver.normalizeHeaderKey(entry.getKey());
				if (!norm.isEmpty()) {
					normalizedHeaderMap.putIfAbsent(norm, entry.getValue());
				}
			}
			validateHeaders(normalizedHeaderMap.keySet());

			int rowNum = 1;
			for (CSVRecord csvRecord : csvParser) {
				boolean anyNonBlank = false;
				for (int i = 0; i < csvRecord.size(); i++) {
					String v = csvRecord.get(i);
					if (v != null && !v.trim().isEmpty()) {
						anyNonBlank = true;
						break;
					}
				}
				if (!anyNonBlank) {
					continue;
				}
				result.setTotalRows(result.getTotalRows() + 1);
				try {
					Function<String, String> valueProvider = normKey -> {
						Integer idx = normalizedHeaderMap.get(normKey);
						if (idx == null || idx >= csvRecord.size())
							return null;
						String val = csvRecord.get(idx);
						return val == null ? null : val.trim();
					};
					rows.add(mapRecordToRow(companyId, valueProvider, normalizedHeaderMap, rowNum));
				} catch (Exception e) {
					result.setFailureCount(result.getFailureCount() + 1);
					result.getErrors().add("CSV row " + rowNum + ": " + e.getMessage());
				}
				rowNum++;
			}
		}
		return rows;
	}

	private List<ImportedCandidateRow> parseExcel(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<ImportedCandidateRow> rows = new ArrayList<>();
		try (Workbook workbook = WorkbookFactory.create(inputStream)) {
			Sheet sheet = workbook.getSheetAt(0);
			if (sheet == null)
				throw new IllegalArgumentException("Excel file is empty or has no sheets.");

			Iterator<Row> rowIterator = sheet.iterator();
			if (!rowIterator.hasNext())
				throw new IllegalArgumentException("Excel sheet is empty.");

			Row headerRow = rowIterator.next();
			Map<String, Integer> normalizedHeaderMap = new HashMap<>();
			DataFormatter dataFormatter = new DataFormatter();
			for (Cell cell : headerRow) {
				String headerText = dataFormatter.formatCellValue(cell).trim();
				String norm = ImportResumeResolver.normalizeHeaderKey(headerText);
				if (!norm.isEmpty())
					normalizedHeaderMap.putIfAbsent(norm, cell.getColumnIndex());
			}
			validateHeaders(normalizedHeaderMap.keySet());

			int excelRowNum = 2;
			while (rowIterator.hasNext()) {
				Row currentRow = rowIterator.next();
				if (isRowEmpty(currentRow)) {
					excelRowNum++;
					continue;
				}
				result.setTotalRows(result.getTotalRows() + 1);

				try {
					Function<String, String> cellValueProvider = normKey -> {
						Integer colIndex = normalizedHeaderMap.get(normKey);
						if (colIndex == null)
							return null;
						Cell cell = currentRow.getCell(colIndex);
						return (cell == null) ? null : dataFormatter.formatCellValue(cell).trim();
					};
					rows.add(mapRecordToRow(companyId, cellValueProvider, normalizedHeaderMap, excelRowNum));
				} catch (Exception e) {
					result.setFailureCount(result.getFailureCount() + 1);
					result.getErrors().add("Excel row " + excelRowNum + ": " + e.getMessage());
				}
				excelRowNum++;
			}
		}
		return rows;
	}

	private ImportedCandidateRow mapRecordToRow(Long companyId, Function<String, String> valueProvider,
			Map<String, Integer> headerMap, int rowNum) {

		CandidateDto dto = new CandidateDto();
		dto.setCompanyId(companyId);

		String name = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Name", "Full Name", "Candidate Name"));
		if (name != null && !name.isBlank()) {
			String[] nameParts = name.trim().split("\\s+", 2);
			dto.setFirstName(nameParts[0]);
			dto.setLastName(nameParts.length > 1 ? nameParts[1] : "");
		} else {
			throw new IllegalArgumentException("Required column 'Name' is missing or empty.");
		}

		String email = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Email", "Work Email", "Official Email"));
		if (email == null || email.isBlank())
			throw new IllegalArgumentException("Required column 'Email' is missing or empty.");
		if (!BASIC_EMAIL.matcher(email.trim()).matches())
			throw new IllegalArgumentException("Invalid 'Email' format: " + email);
		dto.setEmail(email.trim());

		String phone = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Phone Number", "PhoneNumber", "Phone", "Primary Contact", "Mobile", "Contact Number"));
		if (phone == null || phone.isBlank())
			throw new IllegalArgumentException("Required column 'Phone Number' is missing or empty.");
		dto.setPhoneNumber(phone.trim());

		String role = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Role", "Role (Designation)", "Designation", "Department"));
		if (role == null || role.isBlank())
			throw new IllegalArgumentException("Required column 'Role' is missing or empty.");

		String explicitSpec = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Specialization"));
		dto.setSpecialization(explicitSpec != null ? explicitSpec : role.trim());

		String statusRaw = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Status", "Resource Status"));
		if (statusRaw == null || statusRaw.isBlank())
			throw new IllegalArgumentException("Required column 'Status' is missing or empty.");
		dto.setStatus(normalizeCandidateStatus(statusRaw));

		String experienceStr = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Experience", "Experience Years", "Total Experience", "Exp"));
		if (experienceStr == null || experienceStr.isBlank())
			throw new IllegalArgumentException("Required column 'Experience' is missing or empty.");
		Matcher expMatcher = Pattern.compile("^(\\d+)").matcher(experienceStr.trim());
		if (!expMatcher.find())
			throw new IllegalArgumentException("Invalid 'Experience' value: " + experienceStr);
		int years = Integer.parseInt(expMatcher.group(1));
		if (years < 0)
			throw new IllegalArgumentException("Experience cannot be negative: " + experienceStr);
		dto.setExperienceYears(years);

		String resumePath = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Resume Path", "ResumePath", "Resume File", "ResumeFile", "Resume URL", "ResumeUrl", "Resume"));

		String skillsRaw = ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap, List.of("Skills"));
		if (skillsRaw != null && !skillsRaw.isBlank()) {
			dto.setSkillNames(Arrays.stream(skillsRaw.split("[,|]")).map(String::trim)
					.filter(s -> !s.isEmpty()).collect(Collectors.toList()));
		}

		dto.setLocation(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap, List.of("Location")));
		dto.setSourceType(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Source Type", "SourceType")));
		dto.setSourceName(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Source Name", "SourceName")));
		dto.setCurrentCompany(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Current Company", "CurrentCompany")));
		dto.setCurrentCtc(asDouble(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Current CTC", "CurrentCtc"))));
		dto.setExpectedCtc(asDouble(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Expected CTC", "ExpectedCtc"))));
		dto.setNoticePeriod(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Notice Period", "NoticePeriod")));
		dto.setPreferredLocation(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Preferred Location", "PreferredLocation")));
		dto.setPersonalEmailId(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Personal Email", "PersonalEmailId", "PersonalEmail")));
		dto.setGender(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap, List.of("Gender")));
		dto.setDegrees(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap, List.of("Degrees", "Degree")));
		dto.setYearOfPassing(asInteger(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Year of Passing", "YearOfPassing"))));
		dto.setProfileSummary(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Profile Summary", "ProfileSummary")));
		dto.setTrainingSummary(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Training Summary", "TrainingSummary")));
		dto.setCertificationSummary(ImportResumeResolver.firstNonBlankValue(valueProvider, headerMap,
				List.of("Certification Summary", "CertificationSummary")));

		return new ImportedCandidateRow(dto, resumePath, rowNum);
	}

	private static String normalizeCandidateStatus(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.equals("allocated") || s.equals("billable") || s.equals("isbillable") || s.equals("available"))
			return "isBillable";
		if (s.equals("engaged"))
			return "engaged";
		if (s.equals("not allocated") || s.equals("notallocated") || s.equals("notavailable") || s.equals("bench"))
			return "notAvailable";
		throw new IllegalArgumentException(
				"Invalid Status '" + raw + "'. Allowed values: Allocated, Engaged, Not Allocated, Billable, Bench");
	}

	private Double asDouble(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			return Double.parseDouble(s.trim().replace(",", ""));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private void validateHeaders(Set<String> normalizedHeaders) {
		List<String> missing = new ArrayList<>();
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders, List.of("Name", "Full Name", "Candidate Name")))
			missing.add("Name");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders, List.of("Email", "Work Email", "Official Email")))
			missing.add("Email");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders,
				List.of("Role", "Role (Designation)", "Designation", "Department")))
			missing.add("Role");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders, List.of("Status", "Resource Status")))
			missing.add("Status");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders,
				List.of("Experience", "Experience Years", "Total Experience", "Exp")))
			missing.add("Experience");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders,
				List.of("Phone Number", "PhoneNumber", "Phone", "Primary Contact", "Mobile", "Contact Number")))
			missing.add("Phone Number");
		if (!ImportResumeResolver.hasAnyHeader(normalizedHeaders,
				List.of("Resume Path", "ResumePath", "Resume File", "ResumeFile", "Resume URL", "ResumeUrl", "Resume")))
			missing.add("Resume Path");

		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Missing required header column(s): " + String.join(", ", missing));
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
		
		dto.setMiddleName(c.getMiddleName());
		dto.setDateOfBirth(c.getDateOfBirth());
		dto.setPrimaryCountryCode(c.getPrimaryCountryCode());
		dto.setPrimaryContactNo(c.getPrimaryContactNo());
		dto.setSecondaryCountryCode(c.getSecondaryCountryCode());
		dto.setSecondaryContactNo(c.getSecondaryContactNo());
		dto.setCountryOfCitizenship(c.getCountryOfCitizenship());
		dto.setDocumentType(c.getDocumentType());
		dto.setDocumentNumber(c.getDocumentNumber());
		dto.setSecurityClearance(c.getSecurityClearance());
		dto.setVisa(c.getVisa());
		dto.setVisaType(c.getVisaType());
		dto.setCountry(c.getCountry());
		dto.setState(c.getState());
		dto.setCity(c.getCity());
		dto.setZipCode(c.getZipCode());
		dto.setStreet(c.getStreet());
		dto.setAvailabilityToJoin(c.getAvailabilityToJoin());
		dto.setInterviewAvailability(c.getInterviewAvailability());
		dto.setHighestQualification(c.getHighestQualification());
		dto.setUniversityName(c.getUniversityName());
		dto.setDateOfQualification(c.getDateOfQualification());
		dto.setUsaDegree(c.getUsaDegree());
		dto.setCurrentJobTitle(c.getCurrentJobTitle());
		dto.setMostRecentEmployer(c.getMostRecentEmployer());
		dto.setTotalExperience(c.getTotalExperience());
		dto.setRelocate(c.getRelocate());
		dto.setCurrency(c.getCurrency());
		dto.setFrequency(c.getFrequency());
		dto.setSourcingRate(c.getSourcingRate());
		dto.setResumeSummary(c.getResumeSummary());
		dto.setSuggestedKeywords(c.getSuggestedKeywords());
		dto.setSocialLinks(c.getSocialLinksJson());
		dto.setCurrentAccountId(c.getCurrentAccountId());
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
		c.setPersonalEmailId((dto.getPersonalEmailId() != null && dto.getPersonalEmailId().isBlank()) ? null : dto.getPersonalEmailId());
		c.setComments(dto.getComments());
		
		c.setMiddleName(dto.getMiddleName());
		c.setDateOfBirth(dto.getDateOfBirth());
		c.setPrimaryCountryCode(dto.getPrimaryCountryCode());
		c.setPrimaryContactNo(dto.getPrimaryContactNo());
		c.setSecondaryCountryCode(dto.getSecondaryCountryCode());
		c.setSecondaryContactNo(dto.getSecondaryContactNo());
		c.setCountryOfCitizenship(dto.getCountryOfCitizenship());
		c.setDocumentType(dto.getDocumentType());
		c.setDocumentNumber(dto.getDocumentNumber());
		c.setSecurityClearance(dto.getSecurityClearance());
		c.setVisa(dto.getVisa());
		c.setVisaType(dto.getVisaType());
		c.setCountry(dto.getCountry());
		c.setState(dto.getState());
		c.setCity(dto.getCity());
		c.setZipCode(dto.getZipCode());
		c.setStreet(dto.getStreet());
		c.setAvailabilityToJoin(dto.getAvailabilityToJoin());
		c.setInterviewAvailability(dto.getInterviewAvailability());
		c.setHighestQualification(dto.getHighestQualification());
		c.setUniversityName(dto.getUniversityName());
		c.setDateOfQualification(dto.getDateOfQualification());
		c.setUsaDegree(dto.getUsaDegree());
		c.setCurrentJobTitle(dto.getCurrentJobTitle());
		c.setMostRecentEmployer(dto.getMostRecentEmployer());
		c.setTotalExperience(dto.getTotalExperience());
		c.setRelocate(dto.getRelocate());
		c.setCurrency(dto.getCurrency());
		c.setFrequency(dto.getFrequency());
		c.setSourcingRate(dto.getSourcingRate());
		c.setResumeSummary(dto.getResumeSummary());
		c.setSuggestedKeywords(dto.getSuggestedKeywords());
		c.setSocialLinksJson(dto.getSocialLinks());
		c.setCurrentAccountId(dto.getCurrentAccountId());
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
	
		c.setMiddleName(trimTo(c.getMiddleName(), 100));
		c.setPrimaryCountryCode(trimTo(c.getPrimaryCountryCode(), 10));
		c.setPrimaryContactNo(trimTo(c.getPrimaryContactNo(), 20));
		c.setSecondaryCountryCode(trimTo(c.getSecondaryCountryCode(), 10));
		c.setSecondaryContactNo(trimTo(c.getSecondaryContactNo(), 20));
		c.setCountryOfCitizenship(trimTo(c.getCountryOfCitizenship(), 100));
		c.setDocumentType(trimTo(c.getDocumentType(), 50));
		c.setDocumentNumber(trimTo(c.getDocumentNumber(), 100));
		c.setSecurityClearance(trimTo(c.getSecurityClearance(), 100));
		c.setVisa(trimTo(c.getVisa(), 50));
		c.setVisaType(trimTo(c.getVisaType(), 50));
		c.setCountry(trimTo(c.getCountry(), 100));
		c.setState(trimTo(c.getState(), 100));
		c.setCity(trimTo(c.getCity(), 100));
		c.setZipCode(trimTo(c.getZipCode(), 20));
		c.setStreet(trimTo(c.getStreet(), 255));
		c.setAvailabilityToJoin(trimTo(c.getAvailabilityToJoin(), 100));
		c.setInterviewAvailability(trimTo(c.getInterviewAvailability(), 100));
		c.setHighestQualification(trimTo(c.getHighestQualification(), 255));
		c.setUniversityName(trimTo(c.getUniversityName(), 255));
		c.setUsaDegree(trimTo(c.getUsaDegree(), 50));
		c.setCurrentJobTitle(trimTo(c.getCurrentJobTitle(), 255));
		c.setMostRecentEmployer(trimTo(c.getMostRecentEmployer(), 255));
		c.setRelocate(trimTo(c.getRelocate(), 50));
		c.setCurrency(trimTo(c.getCurrency(), 20));
		c.setFrequency(trimTo(c.getFrequency(), 50));
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
			candidateDocumentRepo.saveAndFlush(doc);

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
		dto.setDocuments(loadCandidateDocuments(dto.getCandidateId()));

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

	private void storeAdditionalCandidateDocuments(Candidate candidate, List<MultipartFile> documentFiles, String documentData) throws Exception {
		if (candidate == null || candidate.getCandidateId() == null) return;
		if (documentFiles == null || documentFiles.isEmpty()) return;

		List<Map<String, Object>> metaMaps = List.of();
		if (documentData != null && !documentData.isBlank()) {
			try {
				metaMaps = OM.readValue(documentData, new TypeReference<List<Map<String, Object>>>() {});
			} catch (Exception e) {
				log.warn("Invalid candidate documentData JSON, continuing with file names only. Cause: {}", e.getMessage());
			}
		}

		for (int i = 0; i < documentFiles.size(); i++) {
			MultipartFile file = documentFiles.get(i);
			if (file == null || file.isEmpty()) continue;

			Map<String, Object> rawMeta = i < metaMaps.size() ? metaMaps.get(i) : Map.of();
			String docType = stringVal(rawMeta.get("documentType"));
			if (docType == null || docType.isBlank()) docType = "Document";
			if ("resume".equalsIgnoreCase(docType)) continue;

			String preferredName = stringVal(rawMeta.get("documentName"));
			String uploadName = (preferredName != null && !preferredName.isBlank()) ? preferredName : file.getOriginalFilename();
			String mime = (file.getContentType() != null && !file.getContentType().isBlank())
					? file.getContentType()
					: "application/octet-stream";

			var stored = storage.upload(candidate.getCandidateId(), uploadName, mime, file.getInputStream(), file.getSize());

			CandidateDocument doc = new CandidateDocument();
			doc.setCandidateId(candidate.getCandidateId());
			doc.setDocumentName(stored.fileName());
			doc.setFilePath(stored.url());
			doc.setDocumentType(docType);
			doc.setMimeType(mime);
			doc.setSizeBytes(stored.sizeBytes());
			doc.setStorageProvider(stored.storageProvider());
			doc.setStorageKey(stored.key());
			doc.setIsPrimary(false);
			doc.setVersion(1);
			doc.setResumeShareMeta(null);
			doc.setResumeShareStatus(null);
			candidateDocumentRepo.save(doc);
		}
	}

	private String stringVal(Object value) {
		return value == null ? null : String.valueOf(value).trim();
	}

	private List<EmployeeDocumentDto> loadCandidateDocuments(Long candidateId) {
		if (candidateId == null) return List.of();
		return candidateDocumentRepo.findByCandidateIdOrderByDocumentIdDesc(candidateId).stream().map(doc -> {
			EmployeeDocumentDto dto = new EmployeeDocumentDto();
			dto.setDocumentId(doc.getDocumentId());
			dto.setDocumentType(doc.getDocumentType());
			dto.setFileName(doc.getDocumentName());
			dto.setUrl(doc.getFilePath());
			dto.setMimeType(doc.getMimeType());
			dto.setSizeBytes(doc.getSizeBytes());
			dto.setUploadedAt(doc.getUploadedAt() != null ? doc.getUploadedAt().toString() : null);
			dto.setIsPrimary(doc.getIsPrimary());
			dto.setVersion(doc.getVersion());
			return dto;
		}).toList();
	}

	private static String replaceExt(String name, String ext) {
		if (name == null || name.isBlank()) {
			return "resume" + ext;
		}
		int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name) + ext;
	}

	@Override
	public CandidateDto updateStatus(Long id, String status) {
		Candidate c = candidateRepo.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Candidate not found with id: " + id));
		if ("Allocated".equalsIgnoreCase(status) || "Billable".equalsIgnoreCase(status)) {
			c.setStatus("isBillable");
		} else if ("Not Allocated".equalsIgnoreCase(status)) {
			c.setStatus("notAvailable");
		} else {
			c.setStatus(status);
		}
		candidateRepo.save(c);
		return getById(id);
	}
}

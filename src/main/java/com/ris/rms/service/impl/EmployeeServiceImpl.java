package com.ris.rms.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.ris.rms.dto.EmployeeDocumentDto;
import com.ris.rms.dto.EmployeeDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ProjectHistoryDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Allocation;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Department;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;
import com.ris.rms.entity.EmployeeSkill;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequestGroup;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.StatusMaster;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.repository.EmployeeDocumentRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.EmployeeSkillRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResReqGroupRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.StatusMasterRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.DemandService;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.EmployeeService;
import com.ris.rms.service.ResumeStorageService;
import com.ris.rms.service.ResumeStorageService.ResumeResource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

	@Autowired
	@Lazy
	private EmployeeService self;
	private final EmployeeRepository repo;
	private final CompanyRepository companyRepo;
	private final DepartmentRepository departmentRepo;
	private final SkillRepository skillRepo;
	private final EmployeeSkillRepository employeeSkillRepo;
	private final EmployeeDocumentRepository employeeDocumentRepo;
	private final ResumeStorageService storage;
	private final AllocationRepository allocationRepo;
	private final ProjectRepository projectRepo;
	private final AccountRepository accountRepo;
	private final StatusMasterRepository statusMasterRepo;
	private final UserAccountRepository userAccountRepo;
	private final ResReqGroupRepository resReqGroupRepo;
	private final EmailService emailService;
	private final ResourceRequestRepository rrRepo;
	private final DemandRepository demandRepo;
	private final DemandService demandService;
	@PersistenceContext
	private EntityManager em;

	private static final ObjectMapper OM = new ObjectMapper();
	private static final long MAX_RESUME_BYTES = 10L * 1024 * 1024;

	@Override
	public EmployeeDto create(EmployeeDto dto, MultipartFile resume, List<MultipartFile> documentFiles, String documentData) throws IOException, Exception {
		validateCompanyAndDepartmentForCreate(dto);
		validateUniqueEmail(dto.getCompanyId(), dto.getEmail());
		validateUniquePersonalEmail(dto.getCompanyId(), dto.getPersonalEmailId());

		Employee entity = toEntity(dto);
		entity.setEmployeeId(null);
		Employee saved = repo.save(entity);

		handleSkills(saved.getEmployeeId(), dto);
		handleCurrentProjectAndClient(saved, dto);

		if (resume != null && !resume.isEmpty()) {
			validateResume(resume);
			storeResumeDocument(saved, resume, 1);
		}
		storeAdditionalEmployeeDocuments(saved, documentFiles, documentData);

		return buildOutputDto(saved, dto);
	}

	@Override
	@Transactional(readOnly = true)
	public EmployeeDto getById(Long id) {
		Employee e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Employee not found"));

		String companyName = companyRepo.findById(e.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String deptName = (e.getDepartmentId() != null)
				? departmentRepo.findById(e.getDepartmentId()).map(Department::getDepartmentName).orElse(null)
				: null;

		List<Long> skillIds = loadSkillIds(e.getEmployeeId());
		List<String> skills = loadSkillNames(skillIds);

		EmployeeDto out = toDto(e, companyName, deptName, skillIds, skills);
		fillResumeFields(out);

		// Fetch all active allocations
		List<Allocation> allocs = allocationRepo.findByEmployeeIdAndStatusInOrderByStartDateDesc(id,
				List.of("Client", "Internal", "Active"));

		List<String> currentProjects = new ArrayList<>();
		List<String> currentClients = new ArrayList<>();
		Long lastProjectId = null;
		Long lastAccountId = null;
		LocalDate today = LocalDate.now();

		for (Allocation alloc : allocs) {
			if (alloc.getEndDate() != null && alloc.getEndDate().isBefore(today)) {
				continue;
			}

			String projName = null;
			String clientName = null;

			if (alloc.getProjectId() != null) {
				lastProjectId = alloc.getProjectId();
				var p = projectRepo.findById(alloc.getProjectId()).orElse(null);
				if (p != null) {
					projName = p.getProjectName();
					if (p.getAccountId() != null) {
						lastAccountId = p.getAccountId();
						var a = accountRepo.findById(p.getAccountId()).orElse(null);
						if (a != null) {
							clientName = a.getAccountName();
						}
					}
				}
			} else if (alloc.getRequestId() != null) {
				var rr = rrRepo.findById(alloc.getRequestId()).orElse(null);
				if (rr != null && rr.getDemandId() != null) {
					var d = demandRepo.findById(rr.getDemandId()).orElse(null);
					if (d != null) {
						projName = d.getProjectName();
						if (d.getAccountId() != null) {
							lastAccountId = d.getAccountId();
							var a = accountRepo.findById(d.getAccountId()).orElse(null);
							if (a != null) {
								clientName = a.getAccountName();
							}
						}
					}
				}
			}

			if (projName != null) {
				// CHANGED: Removed date from string, just project name
				currentProjects.add(projName);
			}
			if (clientName != null) {
				currentClients.add(clientName);
			}
		}

		if (!currentProjects.isEmpty()) {
			out.setCurrentProject(String.join(", ", currentProjects));
			out.setCurrentClient(String.join(", ", currentClients.stream().distinct().toList()));

			out.setCurrentProjectId(lastProjectId);
			out.setCurrentAccountId(lastAccountId);
		} else {
			out.setCurrentProjectId(e.getCurrentProjectId());
			out.setCurrentAccountId(e.getCurrentAccountId());
			if (e.getCurrentAccountId() != null) {
				accountRepo.findById(e.getCurrentAccountId()).ifPresent(a -> out.setCurrentClient(a.getAccountName()));
			}
		}

		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EmployeeDto> list(Long companyId, String q, String status, Long departmentId, Integer page,
			Integer size) {

		List<Employee> base;
		Sort sort = Sort.by(Sort.Direction.DESC, "employeeId");

		if (companyId != null) {
			base = repo.findAllByCompanyId(companyId).stream()
					.sorted(Comparator.comparing(Employee::getEmployeeId).reversed()).toList();
		} else if (page != null && size != null && page >= 0 && size > 0) {
			base = repo.findAll(PageRequest.of(page, size, sort)).getContent();
		} else {
			base = repo.findAll(sort);
		}

		String ql = q != null ? q.toLowerCase() : null;
		String sl = status != null ? status.toLowerCase() : null;

		List<Employee> ordered = base.stream()
				.filter(e -> departmentId == null || Objects.equals(e.getDepartmentId(), departmentId))
				.filter(e -> sl == null || (e.getStatus() != null && e.getStatus().toLowerCase().contains(sl)))
				.filter(e -> {
					if (ql == null || ql.isBlank())
						return true;
					return (e.getFirstName() != null && e.getFirstName().toLowerCase().contains(ql))
							|| (e.getLastName() != null && e.getLastName().toLowerCase().contains(ql))
							|| (e.getEmail() != null && e.getEmail().toLowerCase().contains(ql))
							|| (e.getLocation() != null && e.getLocation().toLowerCase().contains(ql));
				}).toList();

		Map<Long, String> companyNames = new HashMap<>();
		Map<Long, String> departmentNames = new HashMap<>();
		for (Employee e : ordered) {
			companyNames.computeIfAbsent(e.getCompanyId(),
					id -> companyRepo.findById(id).map(Company::getCompanyName).orElse(null));
			if (e.getDepartmentId() != null) {
				departmentNames.computeIfAbsent(e.getDepartmentId(),
						id -> departmentRepo.findById(id).map(Department::getDepartmentName).orElse(null));
			}
		}

		Map<Long, List<Long>> skillIdsMap = new HashMap<>();
		Map<Long, List<String>> skillNamesMap = new HashMap<>();
		for (Employee e : ordered) {
			List<Long> ids = loadSkillIds(e.getEmployeeId());
			skillIdsMap.put(e.getEmployeeId(), ids);
			skillNamesMap.put(e.getEmployeeId(), loadSkillNames(ids));
		}

		List<Long> empIds = ordered.stream().map(Employee::getEmployeeId).toList();
		Map<Long, List<ProjectHistoryDto>> historyByEmp = new HashMap<>();
		Map<Long, String> currentClientByEmp = new HashMap<>();
		Map<Long, String> currentProjectByEmp = new HashMap<>();
		Map<Long, Long> currentProjectIdByEmp = new HashMap<>();
		Map<Long, Long> currentAccountIdByEmp = new HashMap<>();

		if (!empIds.isEmpty()) {
			List<Object[]> rows = repo.findEmployeeProjectRows(empIds);
			for (Object[] r : rows) {
				Long empId = ((Number) r[0]).longValue();
				String project = (String) r[1];
				String client = (String) r[2];
				LocalDate start = (r[3] != null) ? ((Date) r[3]).toLocalDate() : null;
				LocalDate end = (r[4] != null) ? ((Date) r[4]).toLocalDate() : null;
				Long projectId = (r.length > 5 && r[5] != null) ? ((Number) r[5]).longValue() : null;
				Long accountId = (r.length > 6 && r[6] != null) ? ((Number) r[6]).longValue() : null;

				ProjectHistoryDto historyItem = new ProjectHistoryDto(project, client, start, end, projectId,
						accountId);
				historyByEmp.computeIfAbsent(empId, k -> new ArrayList<>()).add(historyItem);
			}

			LocalDate today = LocalDate.now();
			for (Long id : empIds) {
				List<ProjectHistoryDto> hist = historyByEmp.get(id);
				if (hist == null || hist.isEmpty())
					continue;

				List<String> activeProjects = new ArrayList<>();
				List<String> activeClients = new ArrayList<>();
				Long lastActiveProjId = null;
				Long lastActiveAccId = null;

				boolean foundActive = false;

				for (ProjectHistoryDto ph : hist) {
					if (ph.getStartDate() != null && (ph.getEndDate() == null || !ph.getEndDate().isBefore(today))) {
						// CHANGED: Removed date from string, just project name
						activeProjects.add(ph.getProjectName() != null ? ph.getProjectName() : "N/A");
						if (ph.getClientName() != null) {
							activeClients.add(ph.getClientName());
						}
						lastActiveProjId = ph.getProjectId();
						lastActiveAccId = ph.getAccountId();
						foundActive = true;
					}
				}

				if (foundActive) {
					currentProjectByEmp.put(id, String.join(", ", activeProjects));
					currentClientByEmp.put(id, String.join(", ", activeClients.stream().distinct().toList()));
					currentProjectIdByEmp.put(id, lastActiveProjId);
					currentAccountIdByEmp.put(id, lastActiveAccId);
				} else {
					ProjectHistoryDto pick = hist.get(0);
					currentClientByEmp.put(id, pick.getClientName() != null ? pick.getClientName() : "N/A");
					currentProjectByEmp.put(id, pick.getProjectName() != null ? pick.getProjectName() : "N/A");
					currentProjectIdByEmp.put(id, pick.getProjectId());
					currentAccountIdByEmp.put(id, pick.getAccountId());
				}
			}
		}

		List<EmployeeDto> finalDtoList = ordered.stream().map(e -> {
			EmployeeDto dto = toDto(e, companyNames.get(e.getCompanyId()),
					e.getDepartmentId() != null ? departmentNames.get(e.getDepartmentId()) : null,
					skillIdsMap.get(e.getEmployeeId()), skillNamesMap.get(e.getEmployeeId()));

			List<ProjectHistoryDto> hist = historyByEmp.get(e.getEmployeeId());
			Long fallbackProjectId = e.getCurrentProjectId();
			Long fallbackAccountId = e.getCurrentAccountId();
			String fallbackClient = null;
			if (fallbackAccountId != null) {
				fallbackClient = accountRepo.findById(fallbackAccountId).map(Account::getAccountName).orElse(null);
			}
			dto.setProjectHistory(hist != null ? hist : List.of());
			dto.setCurrentClient(currentClientByEmp.getOrDefault(e.getEmployeeId(), fallbackClient != null ? fallbackClient : "N/A"));
			dto.setCurrentProject(currentProjectByEmp.getOrDefault(e.getEmployeeId(), "N/A"));
			dto.setCurrentProjectId(currentProjectIdByEmp.getOrDefault(e.getEmployeeId(), fallbackProjectId));
			dto.setCurrentAccountId(currentAccountIdByEmp.getOrDefault(e.getEmployeeId(), fallbackAccountId));
			fillResumeFields(dto);
			return dto;
		}).collect(Collectors.toList());

		if (page != null && size != null && page >= 0 && size > 0) {
			int start = page * size;
			if (start < finalDtoList.size()) {
				int end = Math.min(start + size, finalDtoList.size());
				return finalDtoList.subList(start, end);
			}
			return List.of();
		}
		return finalDtoList;
	}

	@Override
	public EmployeeDto update(Long id, EmployeeDto dto, MultipartFile resume, List<MultipartFile> documentFiles, String documentData) throws IOException, Exception {
		Employee existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Employee not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}

		if (dto.getDepartmentId() != null) {
			Department dep = departmentRepo.findById(dto.getDepartmentId())
					.orElseThrow(() -> new IllegalArgumentException("Department not found"));
			if (!Objects.equals(dep.getCompanyId(), existing.getCompanyId())) {
				throw new IllegalArgumentException("Department must belong to the same company");
			}
			existing.setDepartmentId(dto.getDepartmentId());
		}

		if (dto.getFirstName() != null)
			existing.setFirstName(dto.getFirstName());
		if (dto.getMiddleName() != null)
			existing.setMiddleName(dto.getMiddleName());
		if (dto.getLastName() != null)
			existing.setLastName(dto.getLastName());
		if (dto.getPhoneNumber() != null)
			existing.setPhoneNumber(dto.getPhoneNumber());
		if (dto.getPrimaryCountryCode() != null)
			existing.setPrimaryCountryCode(dto.getPrimaryCountryCode());
		if (dto.getPrimaryContactNo() != null)
			existing.setPrimaryContactNo(dto.getPrimaryContactNo());
		if (dto.getSecondaryCountryCode() != null)
			existing.setSecondaryCountryCode(dto.getSecondaryCountryCode());
		if (dto.getSecondaryContactNo() != null)
			existing.setSecondaryContactNo(dto.getSecondaryContactNo());
		if (dto.getExperienceYears() != null)
			existing.setExperienceYears(dto.getExperienceYears());
		if (dto.getLocation() != null)
			existing.setLocation(dto.getLocation());
		if (dto.getJoiningDate() != null)
			existing.setJoiningDate(dto.getJoiningDate());
		if (dto.getEmploymentType() != null)
			existing.setEmploymentType(dto.getEmploymentType());
		if (dto.getStatus() != null)
			existing.setStatus(dto.getStatus());
		if (dto.getCurrentProjectId() != null)
			existing.setCurrentProjectId(dto.getCurrentProjectId());
		if (dto.getCurrentAccountId() != null)
			existing.setCurrentAccountId(dto.getCurrentAccountId());
		if (dto.getJobTitle() != null)
			existing.setJobTitle(dto.getJobTitle());
		if (dto.getGender() != null)
			existing.setGender(dto.getGender());
		if (dto.getPersonalEmailId() != null && !dto.getPersonalEmailId().trim().isEmpty()
				&& !dto.getPersonalEmailId().equalsIgnoreCase(existing.getPersonalemailid())) {
			validateUniquePersonalEmail(existing.getCompanyId(), dto.getPersonalEmailId(), id);
			existing.setPersonalemailid(dto.getPersonalEmailId());
		}
		// Identity
		if (dto.getDateOfBirth() != null)
			existing.setDateOfBirth(dto.getDateOfBirth());
		if (dto.getCountryOfCitizenship() != null)
			existing.setCountryOfCitizenship(dto.getCountryOfCitizenship());
		if (dto.getDocumentType() != null)
			existing.setDocumentType(dto.getDocumentType());
		if (dto.getDocumentNumber() != null)
			existing.setDocumentNumber(dto.getDocumentNumber());
		if (dto.getSecurityClearance() != null)
			existing.setSecurityClearance(dto.getSecurityClearance());
		if (dto.getVisa() != null)
			existing.setVisa(dto.getVisa());
		if (dto.getVisaType() != null)
			existing.setVisaType(dto.getVisaType());
		// Address
		if (dto.getCountry() != null)
			existing.setCountry(dto.getCountry());
		if (dto.getState() != null)
			existing.setState(dto.getState());
		if (dto.getCity() != null)
			existing.setCity(dto.getCity());
		if (dto.getZipCode() != null)
			existing.setZipCode(dto.getZipCode());
		if (dto.getStreet() != null)
			existing.setStreet(dto.getStreet());
		if (dto.getAvailabilityToJoin() != null)
			existing.setAvailabilityToJoin(dto.getAvailabilityToJoin());
		if (dto.getInterviewAvailability() != null)
			existing.setInterviewAvailability(dto.getInterviewAvailability());
		// Education
		if (dto.getDegrees() != null)
			existing.setDegrees(dto.getDegrees());
		if (dto.getSpecialization() != null)
			existing.setSpecialization(dto.getSpecialization());
		if (dto.getYearOfPassing() != null)
			existing.setYearofpassing(dto.getYearOfPassing());
		if (dto.getHighestQualification() != null)
			existing.setHighestQualification(dto.getHighestQualification());
		if (dto.getUniversityName() != null)
			existing.setUniversityName(dto.getUniversityName());
		if (dto.getDateOfQualification() != null)
			existing.setDateOfQualification(dto.getDateOfQualification());
		if (dto.getUsaDegree() != null)
			existing.setUsaDegree(dto.getUsaDegree());
		// Work
		if (dto.getCurrentJobTitle() != null)
			existing.setCurrentJobTitle(dto.getCurrentJobTitle());
		if (dto.getMostRecentEmployer() != null)
			existing.setMostRecentEmployer(dto.getMostRecentEmployer());
		if (dto.getTotalExperience() != null)
			existing.setTotalExperience(dto.getTotalExperience());
		if (dto.getRelocate() != null)
			existing.setRelocate(dto.getRelocate());
		// Compensation
		if (dto.getCurrency() != null)
			existing.setCurrency(dto.getCurrency());
		if (dto.getFrequency() != null)
			existing.setFrequency(dto.getFrequency());
		if (dto.getSourcingRate() != null)
			existing.setSourcingRate(dto.getSourcingRate());
		// Summaries
		if (dto.getProfileSummary() != null)
			existing.setProfilesummary(dto.getProfileSummary());
		if (dto.getTrainingSummary() != null)
			existing.setTrainingsummary(dto.getTrainingSummary());
		if (dto.getCertificationSummary() != null)
			existing.setCertificationsummary(dto.getCertificationSummary());
		if (dto.getResumeSummary() != null)
			existing.setResumeSummary(dto.getResumeSummary());
		// Skills
		if (dto.getPrimarySkills() != null)
			existing.setPrimarySkillsJson(listToJson(dto.getPrimarySkills()));
		if (dto.getSecondarySkills() != null)
			existing.setSecondarySkillsJson(listToJson(dto.getSecondarySkills()));
		if (dto.getSuggestedKeywords() != null)
			existing.setSuggestedKeywords(dto.getSuggestedKeywords());
		// Social links
		if (dto.getSocialLinks() != null)
			existing.setSocialLinksJson(mapListToJson(dto.getSocialLinks()));

		existing.setUpdateddt(OffsetDateTime.now());
		Employee saved = repo.save(existing);

		handleSkills(saved.getEmployeeId(), dto);
		handleCurrentProjectAndClient(saved, dto);

		if (resume != null && !resume.isEmpty()) {
			validateResume(resume);
			final int oldVersion = employeeDocumentRepo.findPrimaryResume(saved.getEmployeeId()).map(oldDoc -> {
				oldDoc.setIsPrimary(false);
				employeeDocumentRepo.save(oldDoc);
				return oldDoc.getVersion() != null ? oldDoc.getVersion() : 1;
			}).orElse(0);
			storeResumeDocument(saved, resume, oldVersion + 1);
		}
		storeAdditionalEmployeeDocuments(saved, documentFiles, documentData);

		return buildOutputDto(saved, dto);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id))
			throw new IllegalArgumentException("Employee not found");
		repo.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public ResumeResource getResumeByEmployeeId(Long employeeId) throws Exception {
		EmployeeDocument doc = employeeDocumentRepo.findPrimaryResume(employeeId)
				.orElseThrow(() -> new IllegalArgumentException("Resume not found for employee: " + employeeId));

		ResumeStorageService.ResumeResource original = storage.load(doc);

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
			log.warn("Resume preview conversion failed for employee {}. Serving original file instead. Cause: {}",
					employeeId, ex.getMessage());
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
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ImportResultDto importEmployees(Long companyId, InputStream inputStream, String filename) throws Exception {
		ImportResultDto result = new ImportResultDto();
		List<EmployeeDto> employeesToCreate = new ArrayList<>();

		if (filename == null || filename.isBlank())
			throw new IllegalArgumentException("Filename is required to determine file type.");

		String lowerFilename = filename.toLowerCase();

		try {
			if (lowerFilename.endsWith(".csv")) {
				employeesToCreate = parseCsv(companyId, inputStream, result);
			} else if (lowerFilename.endsWith(".xlsx")) {
				employeesToCreate = parseExcel(companyId, inputStream, result);
			} else {
				throw new IllegalArgumentException("Unsupported file type. Please upload a .csv or .xlsx file.");
			}
		} catch (Exception e) {
			result.getErrors().add("Error parsing file: " + e.getMessage());
			result.setFailureCount(result.getTotalRows());
			return result;
		}

		for (EmployeeDto dto : employeesToCreate) {
			try {
				self.create(dto, null, null, null);
				result.setSuccessCount(result.getSuccessCount() + 1);
			} catch (Exception e) {
				result.setFailureCount(result.getFailureCount() + 1);
				String errorMsg = String.format("Error creating employee '%s %s' (Email: %s): %s", dto.getFirstName(),
						dto.getLastName(), dto.getEmail(), e.getMessage());
				result.getErrors().add(errorMsg);
			}
		}
		return result;
	}

	@Override
	@Transactional
	public ResumeShareDto shareResume(ResumeShareDto request) throws Exception {
		StatusMaster status = statusMasterRepo.findByCategoryAndCode("RESUME", request.getStatus())
				.orElseThrow(() -> new IllegalArgumentException("Invalid status code: " + request.getStatus()));

		Employee employee = repo.findById(request.getEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException("Employee not found: " + request.getEmployeeId()));

		String employeeName = ((employee.getFirstName() == null ? "" : employee.getFirstName()) + " "
				+ (employee.getLastName() == null ? "" : employee.getLastName())).trim();

		UserAccount actionUser = userAccountRepo.findById(request.getActionByUserId()).orElseThrow(
				() -> new IllegalArgumentException("Action user not found: " + request.getActionByUserId()));

		String actionByUserName = (actionUser.getEmployeeId() != null ? repo.findById(actionUser.getEmployeeId())
				.map(e -> ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
						+ (e.getLastName() == null ? "" : e.getLastName())).trim())
				.filter(s -> !s.isBlank()).orElse(null) : null);
		if (actionByUserName == null || actionByUserName.isBlank())
			actionByUserName = actionUser.getEmail();

		EmployeeDocument document = employeeDocumentRepo.findPrimaryResume(request.getEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException(
						"No resume available for this employee. Please upload a resume to proceed."));

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

		request.setEmployeeName(employeeName);
		String statusCode = status.getCode();
		request.setStatusSet(statusCode);
		request.setActionByUserName(actionByUserName);
		request.setActionAt(ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime());

		boolean shouldEmail = "Shared".equalsIgnoreCase(statusCode);

		ResumeStorageService.ResumeResource resumeResource = null;
		if (shouldEmail) {
			resumeResource = storage.load(document);
			if (resumeResource == null) {
				throw new IllegalStateException("Failed to load resume file from storage");
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
							+ "' state. Cannot share resume.");
				}

				// DB-backed duplicate check: same employee already shared to same demand
				if (rrRepo.existsByDemandIdAndEmployeeId(dId, request.getEmployeeId())) {
					throw new IllegalArgumentException(
							"Resume has already been shared for Demand: " + demand.getDemandtitle());
				}

				// JSONB history duplicate check (legacy)
				boolean alreadySharedLegacy = history.stream().anyMatch(h -> "DEMAND".equals(h.get("type"))
						&& h.get("demandId") instanceof Number && ((Number) h.get("demandId")).longValue() == dId
						&& h.get("employeeId") instanceof Number);
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

				// Create a ResourceRequest record linked to this employee + demand
				com.ris.rms.entity.ResourceRequest rr = new com.ris.rms.entity.ResourceRequest();
				rr.setDemandId(dId);
				rr.setEmployeeId(request.getEmployeeId());
				rr.setResourceType("INTERNAL");
				rr.setRequesterUserId(request.getActionByUserId());
				rr.setStatus("Submitted");
				rr.setNumberOfResources(1);
				com.ris.rms.entity.ResourceRequest savedRr = rrRepo.save(rr);
				long createdRequestId = savedRr.getRequestId();

				String emailSentTo = "Not sent";
				if (shouldEmail && actionUser.getEmail() != null && !actionUser.getEmail().isBlank()) {
					try {
						emailService.sendResumeShareEmailAsync(actionUser.getEmail(), clientName, employeeName,
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
				info.put("employeeId", request.getEmployeeId());
				info.put("resourceType", "INTERNAL");
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

				if (project.getAccountId() == null)
					throw new IllegalArgumentException(
							"Project " + project.getProjectId() + " is not linked to an Account.");

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
								account.getAccountName(), employeeName, project.getProjectName(),
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

		em.createNativeQuery("update rms.employee_document " + "   set resume_share_status = :status, "
				+ "       resume_share_meta   = cast(:meta as jsonb) " + " where document_id = :id")
				.setParameter("status", statusCode).setParameter("meta", metaJson)
				.setParameter("id", document.getDocumentId()).executeUpdate();

		request.setSharedWith(history);
		return request;
	}

	private void validateCompanyAndDepartmentForCreate(EmployeeDto dto) {
		companyRepo.findById(dto.getCompanyId()).orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (dto.getDepartmentId() != null) {
			Department dep = departmentRepo.findById(dto.getDepartmentId())
					.orElseThrow(() -> new IllegalArgumentException("Department not found"));
			if (!dep.getCompanyId().equals(dto.getCompanyId()))
				throw new IllegalArgumentException("Department must belong to the same company");
		}
	}

	private void validateUniqueEmail(Long companyId, String email) {
		if (email != null && repo.existsByCompanyIdAndEmailIgnoreCase(companyId, email))
			throw new IllegalArgumentException("Email already exists for this company");
	}

	private void validateUniquePersonalEmail(Long companyId, String personalEmailId) {
		validateUniquePersonalEmail(companyId, personalEmailId, null);
	}

	private void validateUniquePersonalEmail(Long companyId, String personalEmailId, Long excludeId) {
		if (personalEmailId != null && !personalEmailId.isBlank()) {
			boolean exists;
			if (excludeId != null) {
				exists = repo.existsByCompanyIdAndPersonalemailidIgnoreCaseAndEmployeeIdNot(companyId, personalEmailId,
						excludeId);
			} else {
				exists = repo.existsByCompanyIdAndPersonalemailidIgnoreCase(companyId, personalEmailId);
			}
			if (exists) {
				throw new IllegalArgumentException("Personal email already exists for this company");
			}
		}
	}

	private void handleSkills(Long employeeId, EmployeeDto dto) {
		if (dto.getSkillIds() != null) {
			linkSkillsByIds(employeeId, dto.getSkillIds());
		} else if (dto.getSkills() != null && !dto.getSkills().isEmpty()) {
			upsertSkillsForEmployee(employeeId, dto.getSkills());
		}
	}

	private void handleCurrentProjectAndClient(Employee saved, EmployeeDto dto) {
		if (dto.getCurrentProjectId() != null) {
			Project proj = projectRepo.findById(dto.getCurrentProjectId())
					.orElseThrow(() -> new IllegalArgumentException("Project not found: " + dto.getCurrentProjectId()));
			if (!Objects.equals(proj.getCompanyId(), saved.getCompanyId()))
				throw new IllegalArgumentException("Project must belong to the same company");

			if (dto.getCurrentAccountId() != null) {
				Account acc = accountRepo.findById(dto.getCurrentAccountId()).orElseThrow(
						() -> new IllegalArgumentException("Client (account) not found: " + dto.getCurrentAccountId()));
				if (!Objects.equals(acc.getCompanyId(), saved.getCompanyId()))
					throw new IllegalArgumentException("Client must belong to the same company");
				if (proj.getAccountId() != null && !Objects.equals(proj.getAccountId(), acc.getAccountId()))
					throw new IllegalArgumentException("currentClient does not match the project's account");
			}

			ensureSingleActiveAllocation(saved.getEmployeeId(), proj.getProjectId(),
					dto.getJobTitle() != null ? dto.getJobTitle() : "Team Member");

		} else if (dto.getCurrentAccountId() != null) {
			Account acc = accountRepo.findById(dto.getCurrentAccountId()).orElseThrow(
					() -> new IllegalArgumentException("Client (account) not found: " + dto.getCurrentAccountId()));
			if (!Objects.equals(acc.getCompanyId(), saved.getCompanyId()))
				throw new IllegalArgumentException("Client must belong to the same company");
		}
	}

	private EmployeeDto buildOutputDto(Employee saved, EmployeeDto inDto) {
		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		String deptName = (saved.getDepartmentId() != null)
				? departmentRepo.findById(saved.getDepartmentId()).map(Department::getDepartmentName).orElse(null)
				: null;

		List<Long> skillIds = loadSkillIds(saved.getEmployeeId());
		List<String> skills = loadSkillNames(skillIds);

		EmployeeDto out = toDto(saved, companyName, deptName, skillIds, skills);
		fillResumeFields(out);

		if (inDto != null) {
			if (inDto.getCurrentProjectId() != null) {
				projectRepo.findById(inDto.getCurrentProjectId()).ifPresent(p -> {
					out.setCurrentProject(p.getProjectName());
					if (p.getAccountId() != null) {
						accountRepo.findById(p.getAccountId()).ifPresent(a -> out.setCurrentClient(a.getAccountName()));
					}
				});
			} else if (inDto.getCurrentAccountId() != null) {
				accountRepo.findById(inDto.getCurrentAccountId())
						.ifPresent(a -> out.setCurrentClient(a.getAccountName()));
			}
			out.setCurrentProjectId(inDto.getCurrentProjectId());
			out.setCurrentAccountId(inDto.getCurrentAccountId());
		}
		return out;
	}

	private void validateResume(MultipartFile f) {
		if (f.getSize() > MAX_RESUME_BYTES)
			throw new IllegalArgumentException("Resume exceeds 10 MB");

		String name = Optional.ofNullable(f.getOriginalFilename()).orElse("").toLowerCase();
		if (!(name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx")))
			throw new IllegalArgumentException("Unsupported resume file type (allowed: PDF, DOC, DOCX)");
	}

	private void storeResumeDocument(Employee saved, MultipartFile resume, int version) throws Exception {
		String origName = resume.getOriginalFilename();
		String lower = (origName == null ? "" : origName.toLowerCase());
		String contentType = resume.getContentType();

		boolean isPdf = "application/pdf".equalsIgnoreCase(contentType) || lower.endsWith(".pdf");
		boolean isDocx = lower.endsWith(".docx")
				|| "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
						.equalsIgnoreCase(contentType);
		boolean isDoc = lower.endsWith(".doc") || "application/msword".equalsIgnoreCase(contentType);

		if (isPdf) {
			var stored = storage.upload(saved.getEmployeeId(), origName, "application/pdf", resume.getInputStream(),
					resume.getSize());

			EmployeeDocument doc = new EmployeeDocument();
			doc.setEmployeeId(saved.getEmployeeId());
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
			employeeDocumentRepo.save(doc);

		} else if (isDoc || isDocx) {
			IConverter converter = null;
			try (InputStream in = resume.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				try {
					converter = LocalConverter.builder().build();
					DocumentType inType = isDocx ? DocumentType.DOCX : DocumentType.DOC;

					boolean ok = converter.convert(in).as(inType).to(out).as(DocumentType.PDF).prioritizeWith(1000)
							.schedule().get();
					if (!ok)
						throw new IllegalStateException("Resume conversion to PDF failed");

					byte[] pdfBytes = out.toByteArray();
					String pdfName = replaceExt(origName, ".pdf");
					var storedPdf = storage.uploadBytes(saved.getEmployeeId(), pdfName, "application/pdf", pdfBytes);

					EmployeeDocument doc = new EmployeeDocument();
					doc.setEmployeeId(saved.getEmployeeId());
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
					employeeDocumentRepo.save(doc);
				} catch (Exception ex) {
					log.warn(
							"DOC/DOCX to PDF conversion failed for employee {}. Storing original resume instead. Cause: {}",
							saved.getEmployeeId(), ex.getMessage());
					String fallbackMime = (contentType == null || contentType.isBlank())
							? (isDocx ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
									: "application/msword")
							: contentType;
					var storedOriginal = storage.upload(saved.getEmployeeId(), origName, fallbackMime,
							resume.getInputStream(),
							resume.getSize());

					EmployeeDocument doc = new EmployeeDocument();
					doc.setEmployeeId(saved.getEmployeeId());
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
					employeeDocumentRepo.save(doc);
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
			throw new IllegalArgumentException("Unsupported resume file type (expected PDF/DOC/DOCX/TXT/RTF)");
		}
	}

	private void fillResumeFields(EmployeeDto dto) {
		var primary = employeeDocumentRepo.findPrimaryResume(dto.getEmployeeId()).orElse(null);
		dto.setDocuments(loadEmployeeDocuments(dto.getEmployeeId()));
		if (primary == null) {
			dto.setResumeShareAudit(List.of());
			return;
		}

		dto.setResumeUrl(primary.getFilePath());
		dto.setResumeFileName(primary.getDocumentName());
		dto.setResumeMimeType(primary.getMimeType());
		if (primary.getUploadedAt() != null)
			dto.setResumeUploadedAt(String.valueOf(primary.getUploadedAt()));
		dto.setResumeDocumentId(primary.getDocumentId());
		dto.setStorageType(primary.getStorageProvider());
		dto.setResumeStatus(primary.getResumeShareStatus());

		if (primary.getResumeShareMeta() != null && !primary.getResumeShareMeta().isBlank()) {
			try {
				var node = OM.readTree(primary.getResumeShareMeta());
				if (node.hasNonNull("actionByUserId"))
					dto.setResumeShareActionByUserId(node.get("actionByUserId").asLong());
				if (node.hasNonNull("actionByUserName"))
					dto.setResumeShareActionByUserName(node.get("actionByUserName").asText());
				if (node.hasNonNull("actionAt"))
					dto.setResumeShareActionAt(node.get("actionAt").asText());

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

	private void storeAdditionalEmployeeDocuments(Employee saved, List<MultipartFile> documentFiles, String documentData) throws Exception {
		if (saved == null || saved.getEmployeeId() == null) return;
		if (documentFiles == null || documentFiles.isEmpty()) return;

		List<Map<String, Object>> metaMaps = List.of();
		if (documentData != null && !documentData.isBlank()) {
			try {
				metaMaps = OM.readValue(documentData, new TypeReference<List<Map<String, Object>>>() {});
			} catch (Exception e) {
				log.warn("Invalid employee documentData JSON, continuing with file names only. Cause: {}", e.getMessage());
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

			var stored = storage.upload(saved.getEmployeeId(), uploadName, mime, file.getInputStream(), file.getSize());

			EmployeeDocument doc = new EmployeeDocument();
			doc.setEmployeeId(saved.getEmployeeId());
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
			employeeDocumentRepo.save(doc);
		}
	}

	private String stringVal(Object value) {
		return value == null ? null : String.valueOf(value).trim();
	}

	private List<EmployeeDocumentDto> loadEmployeeDocuments(Long employeeId) {
		if (employeeId == null) return List.of();
		return employeeDocumentRepo.findByEmployeeIdOrderByDocumentIdDesc(employeeId).stream().map(doc -> {
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
		if (name == null || name.isBlank())
			return "resume" + ext;
		int dot = name.lastIndexOf('.');
		return (dot > 0 ? name.substring(0, dot) : name) + ext;
	}

	private void ensureSingleActiveAllocation(Long employeeId, Long newProjectId, String role) {
		boolean alreadyActiveSameProject = allocationRepo.existsByEmployeeIdAndProjectIdAndStatus(employeeId,
				newProjectId, "Active");
		if (alreadyActiveSameProject)
			return;

		List<Allocation> actives = allocationRepo.findByEmployeeIdAndStatus(employeeId, "Active");
		for (Allocation a : actives) {
			if (!Objects.equals(a.getProjectId(), newProjectId)) {
				a.setStatus("Closed");
				a.setEndDate(LocalDate.now());
				allocationRepo.save(a);
			}
		}

		Allocation allocation = new Allocation();
		allocation.setEmployeeId(employeeId);
		allocation.setProjectId(newProjectId);
		allocation.setRequestId(null);
		allocation.setProjectRole(role);
		allocation.setIsBillable(Boolean.TRUE);
		allocation.setStartDate(LocalDate.now());
		allocation.setEndDate(null);
		allocation.setStatus("Active");
		allocationRepo.save(allocation);
	}

	private void upsertSkillsForEmployee(Long employeeId, List<String> skillNamesIn) {
		List<String> normalized = skillNamesIn.stream().filter(Objects::nonNull).map(String::trim)
				.filter(s -> !s.isBlank()).toList();

		employeeSkillRepo.deleteAllByEmployeeId(employeeId);

		for (String name : normalized) {
			Long skillId = skillRepo.findBySkillNameIgnoreCase(name).map(Skill::getSkillId).orElseGet(() -> {
				Skill s = new Skill();
				s.setSkillName(name);
				return skillRepo.save(s).getSkillId();
			});

			EmployeeSkill es = new EmployeeSkill();
			es.setEmployeeId(employeeId);
			es.setSkillId(skillId);
			employeeSkillRepo.save(es);
		}
	}

	private void linkSkillsByIds(Long employeeId, List<Long> skillIds) {
		employeeSkillRepo.deleteAllByEmployeeId(employeeId);
		if (skillIds == null)
			return;

		for (Long sid : skillIds) {
			if (sid == null)
				continue;
			if (!skillRepo.existsById(sid))
				throw new IllegalArgumentException("Skill not found: " + sid);

			EmployeeSkill es = new EmployeeSkill();
			es.setEmployeeId(employeeId);
			es.setSkillId(sid);
			employeeSkillRepo.save(es);
		}
	}

	private List<Long> loadSkillIds(Long employeeId) {
		return employeeSkillRepo.findAllByEmployeeId(employeeId).stream().map(EmployeeSkill::getSkillId).toList();
	}

	private List<String> loadSkillNames(List<Long> skillIds) {
		if (skillIds == null || skillIds.isEmpty())
			return List.of();
		return skillRepo.findAllById(skillIds).stream().map(Skill::getSkillName).collect(Collectors.toList());
	}

	private EmployeeDto toDto(Employee e, String companyName, String departmentName, List<Long> skillIds,
			List<String> skills) {

		EmployeeDto dto = new EmployeeDto();
		dto.setEmployeeId(e.getEmployeeId());
		dto.setCompanyId(e.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setFirstName(e.getFirstName());
		dto.setMiddleName(e.getMiddleName());
		dto.setLastName(e.getLastName());
		dto.setEmail(e.getEmail());
		dto.setPhoneNumber(e.getPhoneNumber());
		dto.setPrimaryCountryCode(e.getPrimaryCountryCode());
		dto.setPrimaryContactNo(e.getPrimaryContactNo());
		dto.setSecondaryCountryCode(e.getSecondaryCountryCode());
		dto.setSecondaryContactNo(e.getSecondaryContactNo());
		dto.setDepartmentId(e.getDepartmentId());
		dto.setDepartmentName(departmentName);
		dto.setJobTitle(e.getJobTitle());
		dto.setExperienceYears(e.getExperienceYears());
		dto.setLocation(e.getLocation());
		dto.setJoiningDate(e.getJoiningDate());
		dto.setEmploymentType(e.getEmploymentType());
		dto.setStatus(e.getStatus());
		dto.setCurrentProjectId(e.getCurrentProjectId());
		dto.setCurrentAccountId(e.getCurrentAccountId());
		dto.setSkillIds(skillIds);
		dto.setSkills(skills);
		dto.setGender(e.getGender());
		dto.setPersonalEmailId(e.getPersonalemailid());
		// Identity
		dto.setDateOfBirth(e.getDateOfBirth());
		dto.setCountryOfCitizenship(e.getCountryOfCitizenship());
		dto.setDocumentType(e.getDocumentType());
		dto.setDocumentNumber(e.getDocumentNumber());
		dto.setSecurityClearance(e.getSecurityClearance());
		dto.setVisa(e.getVisa());
		dto.setVisaType(e.getVisaType());
		// Address
		dto.setCountry(e.getCountry());
		dto.setState(e.getState());
		dto.setCity(e.getCity());
		dto.setZipCode(e.getZipCode());
		dto.setStreet(e.getStreet());
		dto.setAvailabilityToJoin(e.getAvailabilityToJoin());
		dto.setInterviewAvailability(e.getInterviewAvailability());
		// Education
		dto.setDegrees(e.getDegrees());
		dto.setSpecialization(e.getSpecialization());
		dto.setYearOfPassing(e.getYearofpassing());
		dto.setHighestQualification(e.getHighestQualification());
		dto.setUniversityName(e.getUniversityName());
		dto.setDateOfQualification(e.getDateOfQualification());
		dto.setUsaDegree(e.getUsaDegree());
		// Work
		dto.setCurrentJobTitle(e.getCurrentJobTitle());
		dto.setMostRecentEmployer(e.getMostRecentEmployer());
		dto.setTotalExperience(e.getTotalExperience());
		dto.setRelocate(e.getRelocate());
		// Compensation
		dto.setCurrency(e.getCurrency());
		dto.setFrequency(e.getFrequency());
		dto.setSourcingRate(e.getSourcingRate());
		// Summaries
		dto.setProfileSummary(e.getProfilesummary());
		dto.setTrainingSummary(e.getTrainingsummary());
		dto.setCertificationSummary(e.getCertificationsummary());
		dto.setResumeSummary(e.getResumeSummary());
		// Skills JSON
		dto.setPrimarySkills(jsonToList(e.getPrimarySkillsJson()));
		dto.setSecondarySkills(jsonToList(e.getSecondarySkillsJson()));
		dto.setSuggestedKeywords(e.getSuggestedKeywords());
		// Social links JSON
		dto.setSocialLinks(jsonToMapList(e.getSocialLinksJson()));
		dto.setCreatedDt(e.getCreateddt());
		dto.setUpdatedDt(e.getUpdateddt());
		return dto;
	}

	private Employee toEntity(EmployeeDto dto) {
		Employee e = new Employee();
		e.setEmployeeId(dto.getEmployeeId());
		e.setCompanyId(dto.getCompanyId());
		e.setFirstName(dto.getFirstName());
		e.setMiddleName(dto.getMiddleName());
		e.setLastName(dto.getLastName());
		e.setEmail(dto.getEmail());
		e.setPhoneNumber(dto.getPhoneNumber());
		e.setPrimaryCountryCode(dto.getPrimaryCountryCode());
		e.setPrimaryContactNo(dto.getPrimaryContactNo());
		e.setSecondaryCountryCode(dto.getSecondaryCountryCode());
		e.setSecondaryContactNo(dto.getSecondaryContactNo());
		e.setDepartmentId(dto.getDepartmentId());
		e.setJobTitle(dto.getJobTitle());
		e.setExperienceYears(dto.getExperienceYears());
		e.setLocation(dto.getLocation());
		e.setJoiningDate(dto.getJoiningDate());
		e.setEmploymentType(dto.getEmploymentType());
		e.setStatus(dto.getStatus());
		e.setCurrentProjectId(dto.getCurrentProjectId());
		e.setCurrentAccountId(dto.getCurrentAccountId());
		e.setGender(dto.getGender());
		e.setPersonalemailid(dto.getPersonalEmailId());
		// Identity
		e.setDateOfBirth(dto.getDateOfBirth());
		e.setCountryOfCitizenship(dto.getCountryOfCitizenship());
		e.setDocumentType(dto.getDocumentType());
		e.setDocumentNumber(dto.getDocumentNumber());
		e.setSecurityClearance(dto.getSecurityClearance());
		e.setVisa(dto.getVisa());
		e.setVisaType(dto.getVisaType());
		// Address
		e.setCountry(dto.getCountry());
		e.setState(dto.getState());
		e.setCity(dto.getCity());
		e.setZipCode(dto.getZipCode());
		e.setStreet(dto.getStreet());
		e.setAvailabilityToJoin(dto.getAvailabilityToJoin());
		e.setInterviewAvailability(dto.getInterviewAvailability());
		// Education
		e.setDegrees(dto.getDegrees());
		e.setSpecialization(dto.getSpecialization());
		e.setYearofpassing(dto.getYearOfPassing());
		e.setHighestQualification(dto.getHighestQualification());
		e.setUniversityName(dto.getUniversityName());
		e.setDateOfQualification(dto.getDateOfQualification());
		e.setUsaDegree(dto.getUsaDegree());
		// Work
		e.setCurrentJobTitle(dto.getCurrentJobTitle());
		e.setMostRecentEmployer(dto.getMostRecentEmployer());
		e.setTotalExperience(dto.getTotalExperience());
		e.setRelocate(dto.getRelocate());
		// Compensation
		e.setCurrency(dto.getCurrency());
		e.setFrequency(dto.getFrequency());
		e.setSourcingRate(dto.getSourcingRate());
		// Summaries
		e.setProfilesummary(dto.getProfileSummary());
		e.setTrainingsummary(dto.getTrainingSummary());
		e.setCertificationsummary(dto.getCertificationSummary());
		e.setResumeSummary(dto.getResumeSummary());
		// Skills JSON
		e.setPrimarySkillsJson(listToJson(dto.getPrimarySkills()));
		e.setSecondarySkillsJson(listToJson(dto.getSecondarySkills()));
		e.setSuggestedKeywords(dto.getSuggestedKeywords());
		// Social links JSON
		e.setSocialLinksJson(mapListToJson(dto.getSocialLinks()));
		return e;
	}

	// --- JSON helpers for List<String> and List<Map> columns ---

	@SuppressWarnings("unchecked")
	private List<String> jsonToList(String json) {
		if (json == null || json.isBlank())
			return null;
		try {
			return OM.readValue(json, List.class);
		} catch (Exception ex) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, String>> jsonToMapList(String json) {
		if (json == null || json.isBlank())
			return null;
		try {
			return OM.readValue(json, List.class);
		} catch (Exception ex) {
			return null;
		}
	}

	private String listToJson(List<?> list) {
		if (list == null || list.isEmpty())
			return null;
		try {
			return OM.writeValueAsString(list);
		} catch (Exception ex) {
			return null;
		}
	}

	private String mapListToJson(List<Map<String, String>> list) {
		if (list == null || list.isEmpty())
			return null;
		try {
			return OM.writeValueAsString(list);
		} catch (Exception ex) {
			return null;
		}
	}

	private List<EmployeeDto> parseCsv(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<EmployeeDto> dtos = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
				CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader()
						.setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

			Map<String, Integer> headerMap = csvParser.getHeaderMap();
			validateHeaders(headerMap.keySet());

			int rowNum = 1;
			for (CSVRecord csvRecord : csvParser) {
				result.setTotalRows(result.getTotalRows() + 1);
				try {
					EmployeeDto dto = mapRecordToDto(companyId, csvRecord::get, headerMap.keySet(), rowNum);
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

	private List<EmployeeDto> parseExcel(Long companyId, InputStream inputStream, ImportResultDto result)
			throws Exception {
		List<EmployeeDto> dtos = new ArrayList<>();
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
					EmployeeDto dto = mapRecordToDto(companyId, cellValueProvider, headerMap.keySet(), rowNum);
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

	private EmployeeDto mapRecordToDto(Long companyId, Function<String, String> valueProvider,
			Set<String> availableHeaders, int rowNum) {

		EmployeeDto dto = new EmployeeDto();
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

		String departmentNameFromRole = valueProvider.apply("Role");
		if (departmentNameFromRole != null && !departmentNameFromRole.isBlank()) {
			String deptName = departmentNameFromRole.trim();
			Long departmentId = departmentRepo.findByCompanyIdAndDepartmentNameIgnoreCase(companyId, deptName)
					.map(Department::getDepartmentId).orElseGet(() -> {
						log.info("Auto-creating new department '{}' for companyId {}", deptName, companyId);
						Department newDept = new Department();
						newDept.setCompanyId(companyId);
						newDept.setDepartmentName(deptName);
						return departmentRepo.save(newDept).getDepartmentId();
					});
			dto.setDepartmentId(departmentId);
		} else {
			dto.setDepartmentId(null);
		}

		String skillsRaw = valueProvider.apply("Skills");
		if (skillsRaw != null && !skillsRaw.isBlank()) {
			dto.setSkills(Arrays.stream(skillsRaw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty())
					.collect(Collectors.toList()));
		}

		dto.setStatus(valueProvider.apply("Status"));
		dto.setLocation(valueProvider.apply("Location"));

		String joiningDateStr = valueProvider.apply("Joining Date");
		if (joiningDateStr != null && !joiningDateStr.isBlank()) {
			LocalDate parsedDate = parseFlexibleDate(joiningDateStr);
			if (parsedDate != null) {
				dto.setJoiningDate(parsedDate);
			} else {
				log.warn("Invalid date format for 'Joining Date' on row {}: '{}'. Skipping date.", rowNum,
						joiningDateStr);
			}
		}

		dto.setEmploymentType(valueProvider.apply("Project Type"));

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

		dto.setJobTitle(valueProvider.apply("JobTitle"));
		dto.setGender(valueProvider.apply("Gender"));
		dto.setPersonalEmailId(valueProvider.apply("PersonalEmailId"));
		dto.setDegrees(valueProvider.apply("Degrees"));
		dto.setSpecialization(valueProvider.apply("Specialization"));
		dto.setYearOfPassing(asInteger(valueProvider.apply("YearOfPassing")));
		dto.setProfileSummary(valueProvider.apply("ProfileSummary"));
		dto.setTrainingSummary(valueProvider.apply("TrainingSummary"));
		dto.setCertificationSummary(valueProvider.apply("CertificationSummary"));
		return dto;
	}

	private LocalDate parseFlexibleDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank())
			return null;

		List<DateTimeFormatter> formatters = Arrays.asList(DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"),
				DateTimeFormatter.ofPattern("MM/dd/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd"));

		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.parse(dateStr, formatter);
			} catch (DateTimeParseException ignored) {
			}
		}
		return null;
	}

	private Integer asInteger(String s) {
		if (s == null || s.isBlank())
			return null;
		try {
			String cleaned = s.trim().replaceAll(",", "").replaceAll("\\.0*$", "");
			return Integer.parseInt(cleaned);
		} catch (NumberFormatException e) {
			log.warn("Could not parse integer from string: '{}'", s);
			return null;
		}
	}

	private void validateHeaders(Set<String> presentHeaders) {
		List<String> requiredHeaders = List.of("name", "email");
		for (String required : requiredHeaders) {
			boolean found = presentHeaders.stream().anyMatch(required::equalsIgnoreCase);
			if (!found)
				throw new IllegalArgumentException("Missing required header column: " + required);
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

}

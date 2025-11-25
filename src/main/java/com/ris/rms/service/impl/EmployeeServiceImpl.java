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
import com.ris.rms.service.EmailService;
import com.ris.rms.service.EmployeeService;
import com.ris.rms.service.ResumeStorageService;

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

	@PersistenceContext
	private EntityManager em;

	private static final ObjectMapper OM = new ObjectMapper();
	private static final long MAX_RESUME_BYTES = 10L * 1024 * 1024;

	@Override
	public EmployeeDto create(EmployeeDto dto, MultipartFile resume) throws IOException, Exception {
		validateCompanyAndDepartmentForCreate(dto);
		validateUniqueEmail(dto.getCompanyId(), dto.getEmail());

		Employee entity = toEntity(dto);
		entity.setEmployeeId(null);
		Employee saved = repo.save(entity);

		handleSkills(saved.getEmployeeId(), dto);
		handleCurrentProjectAndClient(saved, dto);

		if (resume != null && !resume.isEmpty()) {
			validateResume(resume);
			storeResumeDocument(saved, resume, 1);
		}

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
			dto.setProjectHistory(hist != null ? hist : List.of());
			dto.setCurrentClient(currentClientByEmp.getOrDefault(e.getEmployeeId(), "N/A"));
			dto.setCurrentProject(currentProjectByEmp.getOrDefault(e.getEmployeeId(), "N/A"));
			dto.setCurrentProjectId(currentProjectIdByEmp.get(e.getEmployeeId()));
			dto.setCurrentAccountId(currentAccountIdByEmp.get(e.getEmployeeId()));
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
	public EmployeeDto update(Long id, EmployeeDto dto, MultipartFile resume) throws IOException, Exception {
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
		if (dto.getLastName() != null)
			existing.setLastName(dto.getLastName());
		if (dto.getPhoneNumber() != null)
			existing.setPhoneNumber(dto.getPhoneNumber());
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
		if (dto.getJobTitle() != null)
			existing.setJobTitle(dto.getJobTitle());
		if (dto.getGender() != null)
			existing.setGender(dto.getGender());
		if (dto.getDegrees() != null)
			existing.setDegrees(dto.getDegrees());
		if (dto.getSpecialization() != null)
			existing.setSpecialization(dto.getSpecialization());
		if (dto.getYearOfPassing() != null)
			existing.setYearofpassing(dto.getYearOfPassing());
		if (dto.getProfileSummary() != null)
			existing.setProfilesummary(dto.getProfileSummary());
		if (dto.getTrainingSummary() != null)
			existing.setTrainingsummary(dto.getTrainingSummary());
		if (dto.getCertificationSummary() != null)
			existing.setCertificationsummary(dto.getCertificationSummary());

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
	public ResumeStorageService.ResumeResource getResumeByEmployeeId(Long employeeId) throws Exception {
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
				self.create(dto, null);
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
		List<Map<String, Object>> sharedWithList = new ArrayList<>();

        if (request.getDemandIds() != null) {
            for (Long dId : request.getDemandIds()) {
                if (dId == null) continue;

                Demand demand = demandRepo.findById(dId)
                        .orElseThrow(() -> new IllegalArgumentException("Demand not found: " + dId));

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

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("type", "DEMAND");
                info.put("demandId", demand.getDemandid());
                info.put("demandTitle", demand.getDemandtitle());
                info.put("projectName", demand.getProjectName());
                info.put("clientId", clientId);
                info.put("clientName", clientName);
                info.put("emailSentTo", emailSentTo);
                info.put("status", statusCode);
                sharedWithList.add(info);
            }
        }


				if (request.getGroupIds() != null) {
					for (Long groupId : request.getGroupIds()) {
						if (groupId == null)
							continue;

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
						sharedWithList.add(info);
					}
				}

				Map<String, Object> meta = new LinkedHashMap<>();
				meta.put("actionByUserId", request.getActionByUserId());
				meta.put("actionByUserName", request.getActionByUserName());
				meta.put("actionAt", request.getActionAt() != null ? request.getActionAt().toString() : null);
				meta.put("sharedWith", sharedWithList);
				String metaJson = OM.writeValueAsString(meta);

				em.createNativeQuery("update rms.employee_document " + "   set resume_share_status = :status, "
						+ "       resume_share_meta   = cast(:meta as jsonb) " + " where document_id = :id")
						.setParameter("status", statusCode).setParameter("meta", metaJson)
						.setParameter("id", document.getDocumentId()).executeUpdate();

				request.setSharedWith(sharedWithList);
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
		dto.setLastName(e.getLastName());
		dto.setEmail(e.getEmail());
		dto.setPhoneNumber(e.getPhoneNumber());
		dto.setDepartmentId(e.getDepartmentId());
		dto.setDepartmentName(departmentName);
		dto.setJobTitle(e.getJobTitle());
		dto.setExperienceYears(e.getExperienceYears());
		dto.setLocation(e.getLocation());
		dto.setJoiningDate(e.getJoiningDate());
		dto.setEmploymentType(e.getEmploymentType());
		dto.setStatus(e.getStatus());
		dto.setSkillIds(skillIds);
		dto.setSkills(skills);
		dto.setGender(e.getGender());
		dto.setPersonalEmailId(e.getPersonalemailid());
		dto.setDegrees(e.getDegrees());
		dto.setSpecialization(e.getSpecialization());
		dto.setYearOfPassing(e.getYearofpassing());
		dto.setProfileSummary(e.getProfilesummary());
		dto.setTrainingSummary(e.getTrainingsummary());
		dto.setCertificationSummary(e.getCertificationsummary());
		dto.setCreatedDt(e.getCreateddt());
		dto.setUpdatedDt(e.getUpdateddt());
		return dto;
	}

	private Employee toEntity(EmployeeDto dto) {
		Employee e = new Employee();
		e.setEmployeeId(dto.getEmployeeId());
		e.setCompanyId(dto.getCompanyId());
		e.setFirstName(dto.getFirstName());
		e.setLastName(dto.getLastName());
		e.setEmail(dto.getEmail());
		e.setPhoneNumber(dto.getPhoneNumber());
		e.setDepartmentId(dto.getDepartmentId());
		e.setJobTitle(dto.getJobTitle());
		e.setExperienceYears(dto.getExperienceYears());
		e.setLocation(dto.getLocation());
		e.setJoiningDate(dto.getJoiningDate());
		e.setEmploymentType(dto.getEmploymentType());
		e.setStatus(dto.getStatus());
		e.setGender(dto.getGender());
		e.setPersonalemailid(dto.getPersonalEmailId());
		e.setDegrees(dto.getDegrees());
		e.setSpecialization(dto.getSpecialization());
		e.setYearofpassing(dto.getYearOfPassing());
		e.setProfilesummary(dto.getProfileSummary());
		e.setTrainingsummary(dto.getTrainingSummary());
		e.setCertificationsummary(dto.getCertificationSummary());
		return e;
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
package com.ris.rms.service.impl;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataConsolidateFunction;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPivotTable;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ris.rms.dto.DemandCreateDto;
import com.ris.rms.dto.DemandReportRequest;
import com.ris.rms.dto.DemandRequestSummaryDto;
import com.ris.rms.dto.DemandResponseDto;
import com.ris.rms.dto.DemandStageCountsDto;
import com.ris.rms.dto.DetailedResourceReportRequest;
import com.ris.rms.dto.GroupFlowDto;
import com.ris.rms.dto.LevelProgressDto;
import com.ris.rms.dto.ResourceRequestDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Allocation;
import com.ris.rms.entity.CandidateDocument;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;
import com.ris.rms.entity.Interview;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.entity.Skill;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CandidateDocumentRepository;
import com.ris.rms.repository.CandidateRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.repository.EmployeeDocumentRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.InterviewRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.repository.SkillRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.DemandService;
import com.ris.rms.service.EmailService;
import com.ris.rms.service.ResourceRequestService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DemandServiceImpl implements DemandService {

	private final DemandRepository demandRepo;
	private final ResourceRequestService rrService;
	private final ResourceRequestRepository rrRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final DepartmentRepository departmentRepo;
	private final UserAccountRepository userAccountRepo;
	private final EmployeeRepository employeeRepo;
	private final SkillRepository skillRepo;
	private final InterviewRepository interviewRepo;
	private final AllocationRepository allocationRepo;
	private final EmployeeDocumentRepository employeeDocumentRepo;
	private final EmailService emailService;
	private final CandidateRepository candidateRepo;
	private final CandidateDocumentRepository candidateDocumentRepo;

	private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

	@Override
	@Transactional(readOnly = true)
	public byte[] generateExcelReport(DemandReportRequest req) throws Exception {
		if (req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required for permission check.");
		}
		UserAccount user = userAccountRepo.findById(req.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + req.getUserId()));

		Pageable unpaged = Pageable.unpaged(Sort.by(Sort.Direction.DESC, "demandopendt"));
		Page<GroupFlowDto> pageResult = getDemandFlowList(user.getCompanyId(), req.getAccountId(), null, null,
				req.getFromDate(), req.getToDate(), unpaged);

		List<Map<String, Object>> data = transformDemandToNested(pageResult.getContent());

		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerFont.setColor(IndexedColors.WHITE.getIndex());
			headerFont.setFontHeightInPoints((short) 12);
			headerStyle.setFont(headerFont);
			headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
			headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			headerStyle.setAlignment(HorizontalAlignment.CENTER);
			headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			headerStyle.setBorderBottom(BorderStyle.THIN);
			headerStyle.setBorderTop(BorderStyle.THIN);
			headerStyle.setBorderLeft(BorderStyle.THIN);
			headerStyle.setBorderRight(BorderStyle.THIN);
			headerStyle.setWrapText(true);

			CellStyle cellStyle = workbook.createCellStyle();
			Font cellFont = workbook.createFont();
			cellFont.setFontHeightInPoints((short) 10);
			cellStyle.setFont(cellFont);
			cellStyle.setWrapText(true);
			cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
			cellStyle.setBorderBottom(BorderStyle.THIN);
			cellStyle.setBorderTop(BorderStyle.THIN);
			cellStyle.setBorderLeft(BorderStyle.THIN);
			cellStyle.setBorderRight(BorderStyle.THIN);
			cellStyle.setAlignment(HorizontalAlignment.LEFT);

			Sheet sheet1 = workbook.createSheet("Demand Summary");
			String[] headers1 = { "Demand ID", "Demand Name", "Description", "Project", "Account", "Dates", "Requests",
					"Pipeline Summary", "Employees (Resumes)", "Resume Status" };

			Row hRow1 = sheet1.createRow(0);
			hRow1.setHeightInPoints(30);
			for (int i = 0; i < headers1.length; i++) {
				Cell cell = hRow1.createCell(i);
				cell.setCellValue(headers1[i]);
				cell.setCellStyle(headerStyle);
			}

			int r1 = 1;
			for (Map<String, Object> d : data) {
				@SuppressWarnings("unchecked")
				Map<String, Object> info = (Map<String, Object>) d.get("demandInfo");
				@SuppressWarnings("unchecked")
				Map<String, Object> ctx = (Map<String, Object>) d.get("contextInfo");
				@SuppressWarnings("unchecked")
				Map<String, Object> stats = (Map<String, Object>) d.get("statusSummary");

				Row row = sheet1.createRow(r1++);
				int c = 0;

				Cell c1 = row.createCell(c++);
				c1.setCellValue(nvl(info.get("demandId"), "").toString());
				c1.setCellStyle(cellStyle);

				Cell c2 = row.createCell(c++);
				c2.setCellValue(nvl(info.get("title"), "").toString());
				c2.setCellStyle(cellStyle);

				Cell c3 = row.createCell(c++);
				c3.setCellValue(nvl(info.get("description"), "").toString());
				c3.setCellStyle(cellStyle);

				Cell c4 = row.createCell(c++);
				c4.setCellValue(nvl(ctx.get("projectName"), "").toString());
				c4.setCellStyle(cellStyle);

				Cell c5 = row.createCell(c++);
				c5.setCellValue(nvl(ctx.get("accountName"), "").toString());
				c5.setCellStyle(cellStyle);

				String datesStr = String.format("Open: %s\nPlanned: %s\nActual: %s", nvl(info.get("demandOpenDt"), "-"),
						nvl(info.get("fulfilmentDt"), "-"), nvl(info.get("actualFulfilmentDt"), "-"));
				Cell c6 = row.createCell(c++);
				c6.setCellValue(datesStr);
				c6.setCellStyle(cellStyle);

				@SuppressWarnings("unchecked")
				List<String> reqIds = (List<String>) stats.get("requestIds");
				String reqStr = (reqIds != null && !reqIds.isEmpty()) ? String.join(" ", reqIds) : "-";
				Cell c7 = row.createCell(c++);
				c7.setCellValue(reqStr);
				c7.setCellStyle(cellStyle);

				String pipelineStr = String.format("Selected: %s\nAllocated: %s\nOnboarded: %s",
						nvl(stats.get("selected"), "0"), nvl(stats.get("allocated"), "0"),
						nvl(stats.get("onboarded"), "0"));
				Cell c8 = row.createCell(c++);
				c8.setCellValue(pipelineStr);
				c8.setCellStyle(cellStyle);

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> resEmps = (List<Map<String, Object>>) stats.get("resumeEmployees");
				String empStr = "";
				if (resEmps != null && !resEmps.isEmpty()) {
					empStr = resEmps.stream().map(e -> "#" + e.get("employeeId") + "-" + e.get("employeeName") + " ("
							+ e.get("resumeStatus") + ")").collect(Collectors.joining("\n"));
				} else {
					empStr = "-";
				}
				Cell c9 = row.createCell(c++);
				c9.setCellValue(empStr);
				c9.setCellStyle(cellStyle);

				String resStatusStr = String.format("%s\nShared: %s | Rejected: %s",
						nvl(stats.get("resumeStatus"), "-"), nvl(stats.get("resumeSharedCount"), "0"),
						nvl(stats.get("resumeRejectedCount"), "0"));
				Cell c10 = row.createCell(c++);
				c10.setCellValue(resStatusStr);
				c10.setCellStyle(cellStyle);
			}

			for (int i = 0; i < headers1.length; i++) {
				sheet1.autoSizeColumn(i);
				if (sheet1.getColumnWidth(i) > 12000)
					sheet1.setColumnWidth(i, 12000);
				if (sheet1.getColumnWidth(i) < 3000)
					sheet1.setColumnWidth(i, 3000);
			}

			Sheet sheet2 = workbook.createSheet("Detailed Pipeline");
			String[] headers2 = { "Demand ID", "Demand Name", "Request ID", "Candidate", "Interview Status",
					"Allocated", "Onboarded", "Resume" };

			Row hRow2 = sheet2.createRow(0);
			hRow2.setHeightInPoints(30);
			for (int i = 0; i < headers2.length; i++) {
				Cell cell = hRow2.createCell(i);
				cell.setCellValue(headers2[i]);
				cell.setCellStyle(headerStyle);
			}
			sheet2.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers2.length - 1));

			int r2 = 1;
			for (Map<String, Object> d : data) {
				@SuppressWarnings("unchecked")
				Map<String, Object> info = (Map<String, Object>) d.get("demandInfo");
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> pipelines = (List<Map<String, Object>>) d.get("pipelineRows");

				String demandId = nvl(info.get("demandId"), "").toString();
				String demandTitle = nvl(info.get("title"), "").toString();

				if (pipelines != null && !pipelines.isEmpty()) {
					for (Map<String, Object> p : pipelines) {
						Row row = sheet2.createRow(r2++);
						int c = 0;

						Cell c1 = row.createCell(c++);
						c1.setCellValue(demandId);
						c1.setCellStyle(cellStyle);

						Cell c2 = row.createCell(c++);
						c2.setCellValue(demandTitle);
						c2.setCellStyle(cellStyle);

						Cell c3 = row.createCell(c++);
						c3.setCellValue(nvl(p.get("requestId"), "").toString());
						c3.setCellStyle(cellStyle);

						String candStr = String.format("#%s - %s\n%s", nvl(p.get("employeeId"), "-"),
								nvl(p.get("candidateName"), "-"), nvl(p.get("candidateEmail"), "-"));
						Cell c4 = row.createCell(c++);
						c4.setCellValue(candStr);
						c4.setCellStyle(cellStyle);

						Cell c5 = row.createCell(c++);
						c5.setCellValue(nvl(p.get("interviewStatus"), "-").toString());
						c5.setCellStyle(cellStyle);

						Cell c6 = row.createCell(c++);
						c6.setCellValue(nvl(p.get("allocated"), "-").toString());
						c6.setCellStyle(cellStyle);

						Cell c7 = row.createCell(c++);
						c7.setCellValue(nvl(p.get("onboarded"), "-").toString());
						c7.setCellStyle(cellStyle);

						Cell c8 = row.createCell(c++);
						c8.setCellValue(nvl(p.get("resumeStatus"), "-").toString());
						c8.setCellStyle(cellStyle);
					}
				}
			}

			for (int i = 0; i < headers2.length; i++) {
				sheet2.autoSizeColumn(i);
				if (sheet2.getColumnWidth(i) > 12000)
					sheet2.setColumnWidth(i, 12000);
				if (sheet2.getColumnWidth(i) < 3000)
					sheet2.setColumnWidth(i, 3000);
			}

			workbook.write(out);
			return out.toByteArray();
		}
	}

	@Override
	public DemandResponseDto createDemand(DemandCreateDto dto) {
		Specification<Demand> dupSpec = (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();
			p.add(cb.equal(root.get("companyId"), dto.getCompanyId()));
			p.add(cb.equal(root.get("demandtitle"), dto.getDemandTitle()));
			p.add(cb.notEqual(root.get("overallStatus"), "Cancelled"));
			return cb.and(p.toArray(new Predicate[0]));
		};

		long count = demandRepo.count(dupSpec);
		if (count > 0) {
			throw new IllegalArgumentException("A Demand with this title already exists. Please check the list.");
		}

		companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found: " + dto.getCompanyId()));
		accountRepo.findById(dto.getAccountId())
				.orElseThrow(() -> new IllegalArgumentException("Account not found: " + dto.getAccountId()));
		departmentRepo.findById(dto.getDepartmentId())
				.orElseThrow(() -> new IllegalArgumentException("Department not found: " + dto.getDepartmentId()));
		userAccountRepo.findById(dto.getRequesterUserId()).orElseThrow(
				() -> new IllegalArgumentException("Requester user not found: " + dto.getRequesterUserId()));

		Demand demand = new Demand();
		demand.setCompanyId(dto.getCompanyId());
		demand.setRequesterUserId(dto.getRequesterUserId());
		demand.setAccountId(dto.getAccountId());
		demand.setDepartmentId(dto.getDepartmentId());
		demand.setProjectName(dto.getProjectName());
		demand.setDemandtitle(dto.getDemandTitle());
		demand.setDescription(dto.getDescription());

		if (dto.getDemandOpenDt() != null) {
			demand.setDemandopendt(dto.getDemandOpenDt());
		} else {
			demand.setDemandopendt(LocalDate.now());
		}

		demand.setFulfilmentdt(dto.getFulfilmentDt());
		demand.setActualFulfilmentDt(null);

		demand.setYearsofexp(dto.getYearsofexp());
		demand.setSkillIds(dto.getSkillIds());
		demand.setRoleduration(dto.getRoleDuration());
		demand.setWorklocpref(dto.getWorkLocPref());
		demand.setPriority(dto.getPriority());
		demand.setLocationType(dto.getLocationType());
		demand.setWorkMode(dto.getWorkMode());
		demand.setResourceRequestsCount(dto.getResourceRequests());

		demand.setOverallStatus("Open");

		Demand savedDemand = demandRepo.save(demand);

		// Resource Requests are NOT created at demand creation.
		// resourceRequestsCount is only the required headcount target.
		// Actual ResourceRequest records are created when HR shares a resume to this demand.

		return getDemandById(savedDemand.getDemandid());
	}

	@Override
	public DemandResponseDto updateDemand(Long demandId, DemandCreateDto dto) {
		Demand demand = demandRepo.findById(demandId)
				.orElseThrow(() -> new IllegalArgumentException("Demand not found: " + demandId));

		if ("Cancelled".equalsIgnoreCase(demand.getOverallStatus())) {
			throw new IllegalArgumentException("This Demand is Cancelled and cannot be edited.");
		}

		if (dto.getResourceRequests() != null) {
			// Only update the required headcount target — do NOT create/delete ResourceRequest records.
			// Actual ResourceRequest records are created only when HR shares a resume to this demand.
			demand.setResourceRequestsCount(dto.getResourceRequests());
		}

		if (dto.getDemandTitle() != null)
			demand.setDemandtitle(dto.getDemandTitle());
		if (dto.getDescription() != null)
			demand.setDescription(dto.getDescription());
		if (dto.getProjectName() != null)
			demand.setProjectName(dto.getProjectName());
		if (dto.getYearsofexp() != null)
			demand.setYearsofexp(dto.getYearsofexp());
		if (dto.getSkillIds() != null)
			demand.setSkillIds(dto.getSkillIds());
		if (dto.getRoleDuration() != null)
			demand.setRoleduration(dto.getRoleDuration());
		if (dto.getWorkLocPref() != null)
			demand.setWorklocpref(dto.getWorkLocPref());
		if (dto.getPriority() != null)
			demand.setPriority(dto.getPriority());
		if (dto.getLocationType() != null)
			demand.setLocationType(dto.getLocationType());
		if (dto.getWorkMode() != null)
			demand.setWorkMode(dto.getWorkMode());
		if (dto.getFulfilmentDt() != null)
			demand.setFulfilmentdt(dto.getFulfilmentDt());

		if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
			demand.setOverallStatus(dto.getStatus());
		}

		if (dto.getAccountId() != null)
			demand.setAccountId(dto.getAccountId());
		if (dto.getDepartmentId() != null)
			demand.setDepartmentId(dto.getDepartmentId());

		Demand saved = demandRepo.save(demand);
		return getDemandById(saved.getDemandid());
	}

	@Override
	public void updateDemandStatusOnResumeShare(Long demandId) {
		Demand demand = demandRepo.findById(demandId).orElse(null);
		if (demand == null)
			return;

		if ("Open".equalsIgnoreCase(demand.getOverallStatus())) {
			demand.setOverallStatus("InProgress");
			demandRepo.save(demand);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public DemandResponseDto getDemandById(Long demandId) {
		Demand demand = demandRepo.findById(demandId)
				.orElseThrow(() -> new IllegalArgumentException("Demand not found: " + demandId));
		return toResponseDto(demand, true);
	}

	@Override
	@Transactional(readOnly = true)
	public List<DemandResponseDto> listDemands(Long companyId, Long accountId, Long departmentId, String status,
			Integer page, Integer size) {
		Specification<Demand> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (companyId != null) {
				predicates.add(cb.equal(root.get("companyId"), companyId));
			}
			if (accountId != null) {
				predicates.add(cb.equal(root.get("accountId"), accountId));
			}
			if (departmentId != null) {
				predicates.add(cb.equal(root.get("departmentId"), departmentId));
			}
			if (status != null && !status.isBlank()) {
				predicates.add(cb.equal(root.get("overallStatus"), status));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		Sort sort = Sort.by(Sort.Direction.DESC, "createddt");

		int pageNum = (page != null && page >= 0) ? page : 0;
		int pageSize = (size != null && size > 0) ? size : 20;

		Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

		Page<Demand> demandPage = demandRepo.findAll(spec, pageable);

		return demandPage.getContent().stream().map(demand -> toResponseDto(demand, false)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public void generateReport(DemandReportRequest req) {
		if (req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required to send the report.");
		}

		List<String> toEmails = Optional.ofNullable(req.getToEmail()).orElse(Collections.emptyList()).stream()
				.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();

		if (toEmails.isEmpty()) {
			throw new IllegalArgumentException(
					"toEmail is required and must contain at least one valid email address.");
		}

		List<String> ccEmails = Optional.ofNullable(req.getCcEmail()).orElse(Collections.emptyList()).stream()
				.filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList();

		UserAccount user = userAccountRepo.findById(req.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + req.getUserId()));

		Pageable unpaged = Pageable.unpaged(Sort.by(Sort.Direction.DESC, "demandopendt"));

		Page<GroupFlowDto> pageResult = getDemandFlowList(user.getCompanyId(), req.getAccountId(), null, null,
				req.getFromDate(), req.getToDate(), unpaged);

		if (pageResult.isEmpty()) {

		}

		List<Map<String, Object>> nestedData = transformDemandToNested(pageResult.getContent());

		String rangeText = buildDateRangeText(req.getFromDate(), req.getToDate());
		String subject = "Demand Flow Report - " + rangeText;

		String userName = user.getEmail();
		if (user.getEmployeeId() != null) {
			Employee e = employeeRepo.findById(user.getEmployeeId()).orElse(null);
			if (e != null && e.getFirstName() != null && !e.getFirstName().isBlank()) {
				userName = e.getFirstName();
			}
		}

		emailService.sendDemandReportEmailAsync(toEmails, ccEmails, subject, userName, nestedData, rangeText);
	}

	private List<Map<String, Object>> transformDemandToNested(List<GroupFlowDto> rows) {
		Map<Long, List<GroupFlowDto>> byGroup = rows.stream()
				.collect(Collectors.groupingBy(GroupFlowDto::getGroupId, LinkedHashMap::new, Collectors.toList()));

		List<Map<String, Object>> out = new ArrayList<>();

		for (Map.Entry<Long, List<GroupFlowDto>> ge : byGroup.entrySet()) {
			List<GroupFlowDto> groupRows = ge.getValue();
			GroupFlowDto g0 = groupRows.stream().findFirst().orElse(null);
			if (g0 == null) {
				continue;
			}

			Map<String, Object> groupInfo = new LinkedHashMap<>();
			groupInfo.put("demandId", g0.getGroupId());
			groupInfo.put("title", g0.getGroupTitle());
			groupInfo.put("description", g0.getDescription());
			groupInfo.put("createdAt", g0.getGroupCreatedAt() == null ? null : g0.getGroupCreatedAt().toString());
			groupInfo.put("totalRequested", g0.getGroupTotalRequested());
			groupInfo.put("status", g0.getGroupStatus());
			groupInfo.put("priority", g0.getPriority());

			groupInfo.put("demandOpenDt", g0.getDemandOpenDt() == null ? "-" : g0.getDemandOpenDt().toString());
			groupInfo.put("fulfilmentDt", g0.getFulfilmentDt() == null ? "-" : g0.getFulfilmentDt().toString());
			groupInfo.put("actualFulfilmentDt",
					g0.getActualFulfilmentDt() == null ? "-" : g0.getActualFulfilmentDt().toString());

			Map<String, Object> contextInfo = new LinkedHashMap<>();
			contextInfo.put("companyName", g0.getCompanyName());
			contextInfo.put("projectName", g0.getProjectName());
			contextInfo.put("accountName", g0.getAccountName());

			Map<String, Object> statusSummary = new LinkedHashMap<>();
			statusSummary.put("selected", nvl(g0.getSummarySelected(), 0));
			statusSummary.put("allocated", nvl(g0.getSummaryAllocated(), 0));
			statusSummary.put("onboarded", nvl(g0.getSummaryOnboarded(), 0));

			boolean anyResumeShared = false;
			boolean anyResumeUploaded = false;

			int resumeSharedCount = 0;
			int resumeRejectedCount = 0;
			List<Map<String, Object>> resumeEmployees = new ArrayList<>();

			Map<Long, List<GroupFlowDto>> byRequest = groupRows.stream().filter(r -> r.getRequestId() != null).collect(
					Collectors.groupingBy(GroupFlowDto::getRequestId, LinkedHashMap::new, Collectors.toList()));

			List<Map<String, Object>> childRequestDetails = new ArrayList<>();

			for (Map.Entry<Long, List<GroupFlowDto>> re : byRequest.entrySet()) {
				Long reqId = re.getKey();
				List<GroupFlowDto> reqRows = re.getValue();

				Map<Long, List<GroupFlowDto>> byInterview = reqRows.stream().filter(r -> r.getInterviewId() != null)
						.collect(Collectors.groupingBy(GroupFlowDto::getInterviewId, LinkedHashMap::new,
								Collectors.toList()));

				for (Map.Entry<Long, List<GroupFlowDto>> ie : byInterview.entrySet()) {
					List<GroupFlowDto> ivRows = ie.getValue();
					GroupFlowDto i0 = ivRows.get(0);

					Map<String, Object> pipelineRow = new LinkedHashMap<>();
					pipelineRow.put("demandId", g0.getGroupId());
					pipelineRow.put("demandTitle", g0.getGroupTitle());
					pipelineRow.put("requestId", reqId);
					pipelineRow.put("candidateName", i0.getCandidateName());
					pipelineRow.put("candidateEmail", i0.getCandidateEmail());
					pipelineRow.put("employeeId", i0.getCandidateEmployeeId());

					String statusStr = i0.getInterviewOverallStatus();
					pipelineRow.put("interviewStatus", statusStr != null ? statusStr : "In Progress");

					pipelineRow.put("allocated",
							i0.getAllocationId() != null ? "Yes (Alloc #" + i0.getAllocationId() + ")" : "No");

					boolean isOnboarded = ivRows.stream()
							.anyMatch(x -> "ONBOARDING".equalsIgnoreCase(x.getInterviewLevel())
									&& "OnBoarded".equalsIgnoreCase(x.getInterviewLevelStatus()));
					pipelineRow.put("onboarded", isOnboarded ? "On Boarded" : "-");

					String candidateResumeStatus = i0.getCandidateResumeStatus();
					pipelineRow.put("resumeStatus", candidateResumeStatus != null ? candidateResumeStatus : "Pending");

					if (candidateResumeStatus != null) {
						anyResumeUploaded = true;

						String statusLower = candidateResumeStatus.toLowerCase(Locale.ROOT);
						if (statusLower.contains("shared")) {
							anyResumeShared = true;
							resumeSharedCount++;
						} else if (statusLower.contains("reject")) {
							resumeRejectedCount++;
						}

						Long empId = i0.getCandidateEmployeeId();
						String empName = i0.getCandidateName();
						if (empId != null || (empName != null && !empName.isBlank())) {
							Map<String, Object> empMeta = new LinkedHashMap<>();
							empMeta.put("employeeId", empId);
							empMeta.put("employeeName", empName);
							empMeta.put("resumeStatus", candidateResumeStatus);
							resumeEmployees.add(empMeta);
						}
					}

					childRequestDetails.add(pipelineRow);
				}
			}

			String resumeSummary = "No Resumes";
			if (anyResumeShared) {
				resumeSummary = "Shared";
			} else if (anyResumeUploaded) {
				resumeSummary = "Uploaded";
			}

			statusSummary.put("resumeStatus", resumeSummary);
			statusSummary.put("resumeSharedCount", resumeSharedCount);
			statusSummary.put("resumeRejectedCount", resumeRejectedCount);
			statusSummary.put("resumeEmployees", resumeEmployees);

			List<String> reqIds = byRequest.keySet().stream().map(id -> "#" + id).toList();
			statusSummary.put("requestIds", reqIds);

			Map<String, Object> groupBlock = new LinkedHashMap<>();
			groupBlock.put("demandInfo", groupInfo);
			groupBlock.put("contextInfo", contextInfo);
			groupBlock.put("statusSummary", statusSummary);
			groupBlock.put("pipelineRows", childRequestDetails);

			out.add(groupBlock);
		}
		return out;
	}

	private static <T> T nvl(T v, T def) {
		return v == null ? def : v;
	}

	private DemandResponseDto toResponseDto(Demand demand) {
		return toResponseDto(demand, false);
	}

	private DemandResponseDto toResponseDto(Demand demand, boolean recalculateStatus) {
		DemandResponseDto dto = new DemandResponseDto();

		dto.setDemandid(demand.getDemandid());
		dto.setDemandTitle(demand.getDemandtitle());
		dto.setDescription(demand.getDescription());
		dto.setDemandOpenDt(demand.getDemandopendt());
		dto.setCompanyId(demand.getCompanyId());
		dto.setAccountId(demand.getAccountId());
		dto.setDepartmentId(demand.getDepartmentId());
		dto.setProjectName(demand.getProjectName());
		dto.setRequesterUserId(demand.getRequesterUserId());
		dto.setYearsofexp(demand.getYearsofexp());
		dto.setSkillIds(demand.getSkillIds());

		dto.setFulfilmentDt(demand.getFulfilmentdt());
		dto.setActualFulfilmentDt(demand.getActualFulfilmentDt());

		LocalDate target = demand.getFulfilmentdt();
		LocalDate actual = dto.getActualFulfilmentDt();

		if (actual != null && target != null) {
			dto.setFulfilledWithinTarget(!actual.isAfter(target));
		} else {
			dto.setFulfilledWithinTarget(false);
		}

		dto.setRoleDuration(demand.getRoleduration());
		dto.setLocationType(demand.getLocationType());
		dto.setWorkLocPref(demand.getWorklocpref());
		dto.setWorkMode(demand.getWorkMode());
		dto.setPriority(demand.getPriority());
		dto.setOverallStatus(demand.getOverallStatus());
		dto.setCreateddt(demand.getCreateddt());
		dto.setUpdateddt(demand.getUpdateddt());
		dto.setResourceRequestsCount(demand.getResourceRequestsCount());
		// Count only real resource requests (created via resume sharing)
		dto.setSubmittedProfilesCount(rrRepo.countActualByDemandId(demand.getDemandid()));
		if (demand.getCreateddt() != null) {
			dto.setPendingDays(ChronoUnit.DAYS.between(demand.getCreateddt().toLocalDate(), LocalDate.now()));
		} else {
			dto.setPendingDays(0);
		}

		companyRepo.findById(demand.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
		accountRepo.findById(demand.getAccountId()).ifPresent(a -> {
			dto.setAccountName(a.getAccountName());
			dto.setAccountEmail(a.getContactPersonEmail());
		});
		departmentRepo.findById(demand.getDepartmentId()).ifPresent(d -> dto.setDepartmentName(d.getDepartmentName()));
		if (demand.getSkillIds() != null && !demand.getSkillIds().isEmpty()) {
			dto.setSkillName(skillRepo.findAllById(demand.getSkillIds()).stream().map(Skill::getSkillName).toList());
		}
		
		if (demand.getRequesterUserId() != null) {
		    userAccountRepo.findById(demand.getRequesterUserId()).ifPresent(ua -> {
		        dto.setRequesterEmail(ua.getEmail());
		        if (ua.getEmployeeId() != null) {
		            employeeRepo.findById(ua.getEmployeeId())
		                    .ifPresent(e -> dto.setRequesterName(e.getFirstName() + " " + e.getLastName()));
		        }
		    });
		}
		
		if (demand.getUpdatedby() != null) {
		    dto.setUpdatedById(demand.getUpdatedby());
		    userAccountRepo.findById(demand.getUpdatedby()).ifPresent(ua -> {
		        dto.setUpdatedByEmail(ua.getEmail());
		        if (ua.getEmployeeId() != null) {
		            employeeRepo.findById(ua.getEmployeeId())
		                    .ifPresent(e -> dto.setUpdatedByName(e.getFirstName() + " " + e.getLastName()));
		        }
		        if (dto.getUpdatedByName() == null) {
		            dto.setUpdatedByName(ua.getEmail());
		        }
		    });
		}
		dto.setSharedResumes(getResumeShareInfos(demand.getDemandid()));

		// Only process real resource requests (those created via resume sharing)
		List<ResourceRequest> childRequests = rrRepo.findActualByDemandId(demand.getDemandid());

		List<DemandRequestSummaryDto> summaryList = new ArrayList<>();
		DemandStageCountsDto stageCounts = new DemandStageCountsDto();
		stageCounts.setTotal((int) rrRepo.countActualByDemandId(demand.getDemandid()));

		LocalDate lastFulfilmentDate = null;
		int allocatedRequests = 0;

		for (ResourceRequest req : childRequests) {
			DemandRequestSummaryDto summaryItem = new DemandRequestSummaryDto();
			summaryItem.setRequestId(req.getRequestId());

			OffsetDateTime lastUpdate = null;

			if (req.getSubmittedDate() != null) {
				lastUpdate = req.getSubmittedDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
			} else if (demand.getCreateddt() != null) {
				lastUpdate = demand.getCreateddt();
			} else if (demand.getDemandopendt() != null) {
				lastUpdate = demand.getDemandopendt().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
			}

			if (lastUpdate != null) {
				summaryItem.setPendingDays(ChronoUnit.DAYS.between(lastUpdate.toLocalDate(), LocalDate.now()));
			} else {
				summaryItem.setPendingDays(0);
			}

			boolean isFinalState = false;

			Optional<Allocation> alloc = allocationRepo.findFirstByRequestIdOrderByStartDateDesc(req.getRequestId());
			if (alloc.isPresent()) {
				summaryItem.setStage("Allocated");
				summaryItem.setStageReason("Employee allocated on " + alloc.get().getStartDate());
				summaryItem.setLastUpdatedAt(
						alloc.get().getStartDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset()));
				stageCounts.addAllocated();
				isFinalState = true;

				allocatedRequests++;

				if (lastFulfilmentDate == null || alloc.get().getStartDate().isAfter(lastFulfilmentDate)) {
					lastFulfilmentDate = alloc.get().getStartDate();
				}
			}

			if (!isFinalState) {
				Optional<Interview> interviewOpt = interviewRepo
						.findTopByRequestIdOrderByInterviewIdDesc(req.getRequestId());
				if (interviewOpt.isPresent()) {
					Interview interview = interviewOpt.get();
					lastUpdate = interview.getScheduledAt();

					if ("Selected".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Selected");
						summaryItem.setStageReason("Interview Selected, Pending Allocation");
						stageCounts.addSelected();
					} else if ("Rejected".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Rejected");
						summaryItem.setStageReason("Interview Rejected");
						stageCounts.addRejected();
						isFinalState = true;
					} else if ("Scheduled".equalsIgnoreCase(interview.getStatus())) {
						summaryItem.setStage("Interview Scheduled");
						summaryItem.setStageReason("Interview Scheduled");
						stageCounts.addInterviewScheduled();
					} else {
						summaryItem.setStage("Interview In Progress");
						summaryItem.setStageReason("Interview " + interview.getStatus());
						stageCounts.addInterviewInProgress();
					}
				}
			}

			if (summaryItem.getStage() == null) {
				if ("Fulfilled".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Allocated");
					summaryItem.setStageReason("Request fulfilled");
					stageCounts.addAllocated();
					isFinalState = true;

					allocatedRequests++;
				} else if ("Rejected".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Rejected");
					summaryItem.setStageReason("Request Rejected by HR/PM");
					stageCounts.addRejected();
					isFinalState = true;
				} else if ("Cancelled".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Rejected");
					summaryItem.setStageReason("Request Cancelled");
					stageCounts.addRejected();
					isFinalState = true;
				} else if ("Submitted".equalsIgnoreCase(req.getStatus())) {
					summaryItem.setStage("Approval Pending");
					summaryItem.setStageReason("Awaiting HR approval");
					stageCounts.addApprovalPending();
				} else {
					summaryItem.setStage("Open");
					summaryItem.setStageReason("Draft state or no interview yet");
					stageCounts.addOpen();
				}
			}

			summaryItem.setLastUpdatedAt(lastUpdate);
			summaryList.add(summaryItem);
		}

		dto.setRequestsSummary(summaryList);
		dto.setStageCounts(stageCounts);

		Integer totalRequested = demand.getResourceRequestsCount();

		if (recalculateStatus || !"Completed".equalsIgnoreCase(demand.getOverallStatus())) {
			if (totalRequested != null && totalRequested > 0 && allocatedRequests == totalRequested) {
				dto.setOverallStatus("Completed");
				demand.setOverallStatus("Completed");

				if (lastFulfilmentDate != null) {
					demand.setActualFulfilmentDt(lastFulfilmentDate);
					dto.setActualFulfilmentDt(lastFulfilmentDate);

					if (demand.getFulfilmentdt() != null) {
						dto.setFulfilledWithinTarget(!lastFulfilmentDate.isAfter(demand.getFulfilmentdt()));
					}
				}

				demandRepo.save(demand);
			}
		}

		return dto;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<GroupFlowDto> getDemandFlowList(Long companyId, Long accountId, Long departmentId, String status,
			String fromDate, String toDate, Pageable pageable) {

		LocalDate from = parseFlexibleDate(fromDate);
		LocalDate to = parseFlexibleDate(toDate);

		Specification<Demand> spec = (root, query, cb) -> {
			List<Predicate> p = new ArrayList<>();
			if (companyId != null) {
				p.add(cb.equal(root.get("companyId"), companyId));
			}
			if (accountId != null) {
				p.add(cb.equal(root.get("accountId"), accountId));
			}
			if (departmentId != null) {
				p.add(cb.equal(root.get("departmentId"), departmentId));
			}
			if (status != null && !status.isBlank()) {
				p.add(cb.equal(root.get("overallStatus"), status));
			}
			if (from != null) {
				p.add(cb.greaterThanOrEqualTo(root.get("demandopendt"), from));
			}
			if (to != null) {
				p.add(cb.lessThanOrEqualTo(root.get("demandopendt"), to));
			}
			return cb.and(p.toArray(new Predicate[0]));
		};

		Page<Demand> demandPage = demandRepo.findAll(spec, pageable);
		if (demandPage.isEmpty()) {
			return Page.empty(pageable);
		}

		List<Demand> demands = demandPage.getContent().stream()
				.sorted(Comparator.comparing(Demand::getDemandopendt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Demand::getDemandid, Comparator.nullsLast(Comparator.reverseOrder())))
				.toList();

		List<Long> demandIds = demands.stream().map(Demand::getDemandid).toList();

		List<ResourceRequest> allRequests = rrRepo.findAll().stream()
				.filter(rr -> rr.getDemandId() != null && demandIds.contains(rr.getDemandId())).toList();

		Map<Long, List<ResourceRequest>> reqsByDemandMap = allRequests.stream()
				.collect(Collectors.groupingBy(ResourceRequest::getDemandId));

		List<Long> allRequestIds = allRequests.stream().map(ResourceRequest::getRequestId).toList();

		List<Interview> allInterviews = allRequestIds.isEmpty() ? List.of()
				: interviewRepo.findAllByRequestIdIn(allRequestIds);
		Map<Long, List<Interview>> interviewsByReqMap = allInterviews.stream()
				.collect(Collectors.groupingBy(Interview::getRequestId));

		List<Allocation> allAllocations = allRequestIds.isEmpty() ? List.of()
				: allocationRepo.findByRequestIdIn(allRequestIds);
		Map<Long, Allocation> allocByReqMap = allAllocations.stream()
				.collect(Collectors.toMap(Allocation::getRequestId, Function.identity(), (a1, a2) -> a1));

		Map<Long, Map<String, Integer>> summaries = preCalculateDemandSummaries(reqsByDemandMap, interviewsByReqMap,
				allocByReqMap);
		Set<Long> allEmployeeIds = allInterviews.stream().map(Interview::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		allAllocations.stream().map(Allocation::getEmployeeId).filter(Objects::nonNull).forEach(allEmployeeIds::add);

		Map<Long, Employee> employeeMap = allEmployeeIds.isEmpty() ? Map.of()
				: employeeRepo.findAllById(allEmployeeIds).stream()
						.collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));

		Map<Long, EmployeeDocument> resumeMap = allEmployeeIds.isEmpty() ? Map.of()
				: employeeDocumentRepo.findPrimaryResumesForEmployees(new ArrayList<>(allEmployeeIds)).stream()
						.collect(Collectors.toMap(EmployeeDocument::getEmployeeId, Function.identity()));

		Set<Long> allUserIds = new java.util.HashSet<>();
		allInterviews.stream().flatMap(i -> readProgress(i.getLevelProgress()).stream())
				.map(LevelProgressDto::getInterviewerUserId).filter(Objects::nonNull).forEach(allUserIds::add);

		Map<Long, UserAccount> userMap = allUserIds.isEmpty() ? Map.of()
				: userAccountRepo.findAllById(allUserIds).stream()
						.collect(Collectors.toMap(UserAccount::getUserId, Function.identity()));

		Set<Long> userEmployeeIds = userMap.values().stream().map(UserAccount::getEmployeeId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (!userEmployeeIds.isEmpty()) {
			employeeRepo.findAllById(userEmployeeIds).forEach(emp -> employeeMap.putIfAbsent(emp.getEmployeeId(), emp));
		}

		Set<Long> companyIds = demands.stream().map(Demand::getCompanyId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Company> companyMap = companyIds.isEmpty() ? Map.of()
				: companyRepo.findAllById(companyIds).stream()
						.collect(Collectors.toMap(Company::getCompanyId, Function.identity()));

		Set<Long> accountIds = demands.stream().map(Demand::getAccountId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Account> accountMap = accountIds.isEmpty() ? Map.of()
				: accountRepo.findAllById(accountIds).stream()
						.collect(Collectors.toMap(Account::getAccountId, Function.identity()));

		List<GroupFlowDto> flatRows = new ArrayList<>();

		for (Demand d : demands) {
			buildFlatDemandFlowRows(flatRows, d, reqsByDemandMap.getOrDefault(d.getDemandid(), Collections.emptyList()),
					summaries.getOrDefault(d.getDemandid(), Collections.emptyMap()), interviewsByReqMap, allocByReqMap,
					employeeMap, resumeMap, userMap, companyMap, accountMap);
		}

		return new PageImpl<>(flatRows, pageable, demandPage.getTotalElements());
	}

	private Map<Long, Map<String, Integer>> preCalculateDemandSummaries(
			Map<Long, List<ResourceRequest>> reqsByDemandMap, Map<Long, List<Interview>> interviewsByReqMap,
			Map<Long, Allocation> allocByReqMap) {

		Map<Long, Map<String, Integer>> summaryMap = new LinkedHashMap<>();

		for (Map.Entry<Long, List<ResourceRequest>> entry : reqsByDemandMap.entrySet()) {
			Long demandId = entry.getKey();
			List<ResourceRequest> requests = entry.getValue();

			int open = 0;
			int interviewing = 0;
			int selected = 0;
			int allocated = 0;
			int onboarded = 0;
			int rejected = 0;
			int totalInterviewsCount = 0;

			for (ResourceRequest req : requests) {
				Long reqId = req.getRequestId();
				List<Interview> reqInterviews = interviewsByReqMap.getOrDefault(reqId, List.of());
				totalInterviewsCount += reqInterviews.size();

				boolean hasAnyInterview = !reqInterviews.isEmpty();
				Allocation reqAlloc = allocByReqMap.get(reqId);
				boolean hasAllocation = (reqAlloc != null);

				Interview latestInterview = reqInterviews.isEmpty() ? null
						: reqInterviews.stream().max(Comparator.comparing(Interview::getInterviewId,
								Comparator.nullsLast(Comparator.naturalOrder()))).orElse(null);

				boolean anySelectedForRequest = false;
				boolean anyOnboardedForRequest = false;
				boolean anyRejectedInterview = false;

				if (latestInterview != null) {
					String st = latestInterview.getStatus();
					if ("Selected".equalsIgnoreCase(st)) {
						anySelectedForRequest = true;
					} else if ("Rejected".equalsIgnoreCase(st)) {
						anyRejectedInterview = true;
					}

					List<LevelProgressDto> latestLevels = readProgress(latestInterview.getLevelProgress());
					anyOnboardedForRequest = latestLevels.stream()
							.anyMatch(lp -> "ONBOARDING".equalsIgnoreCase(lp.getLevel()) && lp.getStatus() != null
									&& lp.getStatus().replace(" ", "").equalsIgnoreCase("OnBoarded"));
				}

				boolean isRejectedReq = "Rejected".equalsIgnoreCase(req.getStatus())
						|| "Cancelled".equalsIgnoreCase(req.getStatus());

				if (hasAllocation) {
					allocated++;
				}

				boolean isFinalPositive = anySelectedForRequest || anyOnboardedForRequest || hasAllocation;
				boolean isFinalNegative = anyRejectedInterview || isRejectedReq;

				if (!isFinalPositive && isFinalNegative) {
					rejected++;
				}

				if (!isFinalPositive && !isFinalNegative) {
					if (hasAnyInterview) {
						interviewing++;
					} else {
						open++;
					}
				}

				for (Interview iv : reqInterviews) {
					boolean interviewSelected = "Selected".equalsIgnoreCase(iv.getStatus());

					List<LevelProgressDto> levels = readProgress(iv.getLevelProgress());
					boolean interviewOnboarded = levels.stream()
							.anyMatch(lp -> "ONBOARDING".equalsIgnoreCase(lp.getLevel()) && lp.getStatus() != null
									&& lp.getStatus().replace(" ", "").equalsIgnoreCase("OnBoarded"));

					boolean allocationForThisCandidate = false;
					if (reqAlloc != null && iv.getEmployeeId() != null) {
						allocationForThisCandidate = Objects.equals(reqAlloc.getEmployeeId(), iv.getEmployeeId());
					}

					if (interviewOnboarded || allocationForThisCandidate) {
						onboarded++;
					}

					if (interviewSelected || interviewOnboarded || allocationForThisCandidate) {
						selected++;
					}
				}
			}

			summaryMap.put(demandId,
					Map.of("totalRequests", requests.size(), "open", open, "interviewing", interviewing, "selected",
							selected, "allocated", allocated, "onboarded", onboarded, "rejected", rejected,
							"totalInterviews", totalInterviewsCount));
		}

		return summaryMap;
	}

	private void buildFlatDemandFlowRows(List<GroupFlowDto> flatRows, Demand demand,
			List<ResourceRequest> childRequests, Map<String, Integer> summary,
			Map<Long, List<Interview>> interviewsByReqMap, Map<Long, Allocation> allocByReqMap,
			Map<Long, Employee> employeeMap, Map<Long, EmployeeDocument> resumeMap, Map<Long, UserAccount> userMap,
			Map<Long, Company> companyMap, Map<Long, Account> accountMap) {

		GroupFlowDto baseRow = new GroupFlowDto();

		baseRow.setGroupId(demand.getDemandid());
		baseRow.setGroupTitle(demand.getDemandtitle());
		OffsetDateTime openAt = demand.getDemandopendt() == null ? null
				: demand.getDemandopendt().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
		baseRow.setGroupCreatedAt(openAt);
		baseRow.setGroupTotalRequested(demand.getResourceRequestsCount());
		baseRow.setGroupStatus(demand.getOverallStatus());
		baseRow.setDemandOpenDt(demand.getDemandopendt());
		baseRow.setPriority(demand.getPriority());
		baseRow.setRoleDuration(demand.getRoleduration());
		baseRow.setDescription(demand.getDescription());
		baseRow.setFulfilmentDt(demand.getFulfilmentdt());
		baseRow.setActualFulfilmentDt(demand.getActualFulfilmentDt());

		if (demand.getActualFulfilmentDt() != null && demand.getFulfilmentdt() != null) {
			baseRow.setFulfilledWithinTarget(!demand.getActualFulfilmentDt().isAfter(demand.getFulfilmentdt()));
		} else {
			baseRow.setFulfilledWithinTarget(false);
		}
		baseRow.setGroupCreatorUserId(demand.getRequesterUserId());
		if (demand.getRequesterUserId() != null) {
			UserAccount ua = userMap.get(demand.getRequesterUserId());
			if (ua != null) {
				baseRow.setGroupCreatorEmail(ua.getEmail());
				if (ua.getEmployeeId() != null) {
					Employee e = employeeMap.get(ua.getEmployeeId());
					if (e != null) {
						baseRow.setGroupCreatorName(e.getFirstName() + " " + e.getLastName());
					}
				}

				if (baseRow.getGroupCreatorName() == null) {
					baseRow.setGroupCreatorName(ua.getEmail());
				}
			}
		}
		baseRow.setCompanyId(demand.getCompanyId());
		companyMap.computeIfPresent(demand.getCompanyId(), (k, v) -> {
			baseRow.setCompanyName(v.getCompanyName());
			return v;
		});

		if (demand.getAccountId() != null) {
			baseRow.setAccountId(demand.getAccountId());
			accountMap.computeIfPresent(demand.getAccountId(), (k, v) -> {
				baseRow.setAccountName(v.getAccountName());
				return v;
			});
		}

		baseRow.setProjectId(null);
		baseRow.setProjectName(demand.getProjectName());

		baseRow.setSummaryTotalRequests(summary.getOrDefault("totalRequests", 0));
		baseRow.setSummaryOpen(summary.getOrDefault("open", 0));
		baseRow.setSummaryInterviewing(summary.getOrDefault("interviewing", 0));
		baseRow.setSummarySelected(summary.getOrDefault("selected", 0));
		baseRow.setSummaryAllocated(summary.getOrDefault("allocated", 0));
		baseRow.setSummaryOnboarded(summary.getOrDefault("onboarded", 0));
		baseRow.setSummaryRejected(summary.getOrDefault("rejected", 0));
		baseRow.setSummaryTotalInterviews(summary.getOrDefault("totalInterviews", 0));

		long pendingDays = (openAt != null) ? ChronoUnit.DAYS.between(openAt.toLocalDate(), LocalDate.now()) : 0;
		baseRow.setSummaryPendingDays(pendingDays);

		if (childRequests.isEmpty()) {
			flatRows.add(baseRow);
			return;
		}

		for (ResourceRequest req : childRequests) {
			GroupFlowDto reqRow = copyBaseRow(baseRow);
			reqRow.setRequestId(req.getRequestId());
			reqRow.setRequestStatus(req.getStatus());

			List<Interview> interviewsForReq = interviewsByReqMap.getOrDefault(req.getRequestId(),
					Collections.emptyList());

			if (interviewsForReq.isEmpty()) {
				flatRows.add(reqRow);
				continue;
			}

			for (Interview interview : interviewsForReq) {
				GroupFlowDto interviewRow = copyBaseRow(reqRow);
				interviewRow.setInterviewId(interview.getInterviewId());
				interviewRow.setInterviewOverallStatus(interview.getStatus());

				if (interview.getEmployeeId() != null) {

					interviewRow.setResourceType("EMPLOYEE");
					interviewRow.setCandidateEmployeeId(interview.getEmployeeId());
					interviewRow.setCandidateId(null);

					Employee emp = employeeMap.get(interview.getEmployeeId());
					if (emp != null) {
						interviewRow.setCandidateName(emp.getFirstName() + " " + emp.getLastName());
						interviewRow.setCandidateEmail(emp.getEmail());
						interviewRow.setCandidatePhoneNumber(emp.getPhoneNumber());
						interviewRow.setCandidateDesignation(emp.getJobTitle());
						interviewRow.setCandidateExperience(emp.getExperienceYears());

						EmployeeDocument resume = resumeMap.get(emp.getEmployeeId());
						if (resume != null) {
							interviewRow.setCandidateResumeStatus(resume.getResumeShareStatus());
						}
					}
				} else if (interview.getCandidateId() != null) {
					interviewRow.setResourceType("CANDIDATE");
					interviewRow.setCandidateId(interview.getCandidateId());
					interviewRow.setCandidateEmployeeId(null);

					candidateRepo.findById(interview.getCandidateId()).ifPresent(cand -> {
						interviewRow.setCandidateName(cand.getFirstName() + " " + cand.getLastName());
						interviewRow.setCandidateEmail(cand.getEmail());
						interviewRow.setCandidatePhoneNumber(cand.getPhoneNumber());
						interviewRow.setCandidateDesignation("Candidate");
						interviewRow.setCandidateExperience(cand.getExperienceYears());
					});
					candidateDocumentRepo.findPrimaryResume(interview.getCandidateId()).ifPresent(doc -> {
						interviewRow.setCandidateResumeStatus(doc.getResumeShareStatus());
					});
				}
				Allocation alloc = allocByReqMap.get(req.getRequestId());
				if (alloc != null && Objects.equals(alloc.getEmployeeId(), interview.getEmployeeId())) {
					interviewRow.setAllocationId(alloc.getAllocationId());
					Employee allocEmp = employeeMap.get(alloc.getEmployeeId());
					interviewRow.setAllocationEmployeeName(
							allocEmp != null ? allocEmp.getFirstName() + " " + allocEmp.getLastName() : null);
					interviewRow.setAllocationStartDate(alloc.getStartDate());
					interviewRow.setAllocationEndDate(alloc.getEndDate());
					interviewRow.setAllocationProjectRole(alloc.getProjectRole());
					interviewRow.setAllocationIsBillable(alloc.getIsBillable());
				}

				List<LevelProgressDto> levels = readProgress(interview.getLevelProgress());
				if (levels.isEmpty() || levels.stream().allMatch(l -> "__META__".equals(l.getLevel()))) {
					flatRows.add(interviewRow);
					continue;
				}

				for (LevelProgressDto level : levels) {
					if ("__META__".equals(level.getLevel()))
						continue;

					GroupFlowDto levelRow = copyBaseRow(interviewRow);
					levelRow.setInterviewLevel(level.getLevel());
					levelRow.setInterviewLevelStatus(level.getStatus());
					levelRow.setInterviewNotes(level.getInterviewNotes());
					levelRow.setInterviewCompletedAt(level.getCompletedAt());

					if (level.getInterviewerUserId() != null) {
						levelRow.setInterviewerUserId(level.getInterviewerUserId());
						UserAccount user = userMap.get(level.getInterviewerUserId());
						if (user != null) {
							levelRow.setInterviewerEmail(user.getEmail());
							if (user.getEmployeeId() != null) {
								Employee emp = employeeMap.get(user.getEmployeeId());
								if (emp != null) {
									levelRow.setInterviewerName(emp.getFirstName() + " " + emp.getLastName());
								}
							}
							if (levelRow.getInterviewerName() == null) {
								levelRow.setInterviewerName(user.getEmail());
							}
						}
					}

					flatRows.add(levelRow);
				}
			}
		}
	}

	private List<LevelProgressDto> readProgress(String json) {
		try {
			if (json == null || json.isBlank()) {
				return new ArrayList<>();
			}
			return om.readValue(json, new TypeReference<List<LevelProgressDto>>() {
			});
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	private GroupFlowDto copyBaseRow(GroupFlowDto base) {
		GroupFlowDto copy = new GroupFlowDto();

		copy.setGroupId(base.getGroupId());
		copy.setGroupTitle(base.getGroupTitle());
		copy.setGroupCreatedAt(base.getGroupCreatedAt());
		copy.setGroupTotalRequested(base.getGroupTotalRequested());
		copy.setGroupStatus(base.getGroupStatus());

		copy.setGroupCreatorUserId(base.getGroupCreatorUserId());
		copy.setGroupCreatorName(base.getGroupCreatorName());
		copy.setGroupCreatorEmail(base.getGroupCreatorEmail());

		copy.setCompanyId(base.getCompanyId());
		copy.setCompanyName(base.getCompanyName());
		copy.setProjectId(base.getProjectId());
		copy.setProjectName(base.getProjectName());
		copy.setAccountId(base.getAccountId());
		copy.setAccountName(base.getAccountName());
		copy.setDemandOpenDt(base.getDemandOpenDt());
		copy.setFulfilmentDt(base.getFulfilmentDt());
		copy.setActualFulfilmentDt(base.getActualFulfilmentDt());
		copy.setFulfilledWithinTarget(base.getFulfilledWithinTarget());
		copy.setPriority(base.getPriority());
		copy.setRoleDuration(base.getRoleDuration());
		copy.setDescription(base.getDescription());
		copy.setSummaryTotalRequests(base.getSummaryTotalRequests());
		copy.setSummaryOpen(base.getSummaryOpen());
		copy.setSummaryInterviewing(base.getSummaryInterviewing());
		copy.setSummarySelected(base.getSummarySelected());
		copy.setSummaryAllocated(base.getSummaryAllocated());
		copy.setSummaryOnboarded(base.getSummaryOnboarded());
		copy.setSummaryRejected(base.getSummaryRejected());
		copy.setSummaryTotalInterviews(base.getSummaryTotalInterviews());
		copy.setSummaryPendingDays(base.getSummaryPendingDays());

		copy.setRequestId(base.getRequestId());
		copy.setRequestStatus(base.getRequestStatus());

		copy.setInterviewId(base.getInterviewId());
		copy.setInterviewOverallStatus(base.getInterviewOverallStatus());

		copy.setResourceType(base.getResourceType());
		copy.setCandidateId(base.getCandidateId());
		copy.setCandidateEmployeeId(base.getCandidateEmployeeId());
		copy.setCandidateName(base.getCandidateName());
		copy.setCandidateEmail(base.getCandidateEmail());
		copy.setCandidatePhoneNumber(base.getCandidatePhoneNumber());
		copy.setCandidateResumeStatus(base.getCandidateResumeStatus());
		copy.setCandidateDesignation(base.getCandidateDesignation());
		copy.setCandidateExperience(base.getCandidateExperience());

		copy.setAllocationId(base.getAllocationId());
		copy.setAllocationEmployeeName(base.getAllocationEmployeeName());
		copy.setAllocationStartDate(base.getAllocationStartDate());
		copy.setAllocationEndDate(base.getAllocationEndDate());
		copy.setAllocationProjectRole(base.getAllocationProjectRole());
		copy.setAllocationIsBillable(base.getAllocationIsBillable());

		copy.setInterviewLevel(base.getInterviewLevel());
		copy.setInterviewLevelStatus(base.getInterviewLevelStatus());
		copy.setInterviewNotes(base.getInterviewNotes());
		copy.setInterviewCompletedAt(base.getInterviewCompletedAt());
		copy.setInterviewerUserId(base.getInterviewerUserId());
		copy.setInterviewerName(base.getInterviewerName());
		copy.setInterviewerEmail(base.getInterviewerEmail());

		return copy;
	}

	private String buildDateRangeText(String fromDate, String toDate) {
		LocalDate start = parseFlexibleDate(fromDate);
		LocalDate end = parseFlexibleDate(toDate);

		if (start != null && end != null) {
			if (start.equals(end)) {

				return start.toString();
			}

			return start.toString() + " to " + end.toString();
		}
		if (start != null) {
			return start.toString() + " to End";
		}
		if (end != null) {
			return "Start to " + end.toString();
		}
		return "All Time";
	}

	private LocalDate parseFlexibleDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return null;
		}

		List<DateTimeFormatter> formatters = Arrays.asList(DateTimeFormatter.ISO_LOCAL_DATE,
				DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("d/M/yyyy"),
				DateTimeFormatter.ofPattern("MM/dd/yyyy"), DateTimeFormatter.ofPattern("dd-MM-yyyy"),
				DateTimeFormatter.ofPattern("yyyy/MM/dd"));

		for (DateTimeFormatter formatter : formatters) {
			try {
				return LocalDate.parse(dateStr, formatter);
			} catch (DateTimeParseException e) {
			}
		}
		return null;
	}

	// Helper 1: Scans Employee and Candidate documents for share history linked to this demand
	private List<DemandResponseDto.ResumeShareInfo> getResumeShareInfos(Long demandId) {
	    List<DemandResponseDto.ResumeShareInfo> results = new ArrayList<>();

	    // 1. Process Employee Documents
	    List<EmployeeDocument> empDocs = employeeDocumentRepo.findAll(); 
	    for (EmployeeDocument doc : empDocs) {
	        if (StringUtils.hasText(doc.getResumeShareMeta())) {
	            parseAndAddShareInfo(results, doc.getResumeShareMeta(), demandId, doc.getEmployeeId(), "EMPLOYEE");
	        }
	    }

	    // 2. Process Candidate Documents
	    List<CandidateDocument> candDocs = candidateDocumentRepo.findAll();
	    for (CandidateDocument doc : candDocs) {
	        if (StringUtils.hasText(doc.getResumeShareMeta())) {
	            parseAndAddShareInfo(results, doc.getResumeShareMeta(), demandId, doc.getCandidateId(), "CANDIDATE");
	        }
	    }
	    
	    return results;
	}

	// Helper 2: Parses the JSON history and extracts details if the demand matches
	private void parseAndAddShareInfo(List<DemandResponseDto.ResumeShareInfo> results, String jsonMeta, Long targetDemandId, Long resourceId, String type) {
		try {
			if (!StringUtils.hasText(jsonMeta)) return;

			List<ResumeShareDto> history = new ArrayList<>();
			String trimmed = jsonMeta.trim();

			// FIX: Handle both Single Object and List formats
			if (trimmed.startsWith("[")) {
				history = om.readValue(trimmed, new TypeReference<List<ResumeShareDto>>() {});
			} else if (trimmed.startsWith("{")) {
				ResumeShareDto single = om.readValue(trimmed, ResumeShareDto.class);
				history.add(single);
			} else {
				return;
			}

			if (history == null || history.isEmpty()) return;

			for (ResumeShareDto share : history) {
				boolean isMatch = false;

				// Check 1: Look in the 'demandIds' list (if populated)
				if (share.getDemandIds() != null && share.getDemandIds().contains(targetDemandId)) {
					isMatch = true;
				}

				// Check 2: Look in the 'sharedWith' details list (where your data actually is)
				if (!isMatch && share.getSharedWith() != null) {
					for (Map<String, Object> detail : share.getSharedWith()) {
						Object dIdObj = detail.get("demandId");
						if (dIdObj != null) {
							try {
								Long dId = Long.valueOf(dIdObj.toString());
								if (dId.equals(targetDemandId)) {
									isMatch = true;
									break;
								}
							} catch (NumberFormatException e) {
								// ignore invalid numbers
							}
						}
					}
				}

				if (isMatch) {
					DemandResponseDto.ResumeShareInfo info = new DemandResponseDto.ResumeShareInfo();
					info.setResourceId(resourceId);
					info.setResourceType(type);
					
					// Use the 'sharedAt' from the inner detail if available, otherwise the main action time
					info.setSharedAt(share.getActionAt());
					if (share.getSharedWith() != null) {
						for (Map<String, Object> detail : share.getSharedWith()) {
							if (targetDemandId.equals(asLong(detail.get("demandId")))) {
								String specificDate = (String) detail.get("sharedAt");
								if (specificDate != null) {
									try {
										info.setSharedAt(OffsetDateTime.parse(specificDate));
									} catch(Exception e) {}
								}
								break;
							}
						}
					}

					// Resolve "Shared By" User
					if (share.getActionByUserId() != null) {
						userAccountRepo.findById(share.getActionByUserId()).ifPresent(u -> {
							info.setSharedByEmail(u.getEmail());
							if (u.getEmployeeId() != null) {
								employeeRepo.findById(u.getEmployeeId()).ifPresent(e -> 
									info.setSharedBy(e.getFirstName() + " " + e.getLastName())
								);
							}
							if (info.getSharedBy() == null) info.setSharedBy(u.getEmail());
						});
					} else if (share.getActionByUserName() != null) {
						info.setSharedBy(share.getActionByUserName());
					}

					// Resolve Resource Name
					if ("EMPLOYEE".equals(type)) {
						employeeRepo.findById(resourceId).ifPresent(e -> {
							info.setResourceName(e.getFirstName() + " " + e.getLastName());
							info.setResourceEmail(e.getEmail());
						});
					} else {
						candidateRepo.findById(resourceId).ifPresent(c -> {
							info.setResourceName(c.getFirstName() + " " + c.getLastName());
							info.setResourceEmail(c.getEmail());
						});
					}
					
					results.add(info);
				}
			}
		} catch (Exception e) {
			log.warn("Failed to parse resume share meta for {} {}: {}", type, resourceId, e.getMessage());
		}
	}

    // Small helper to safely cast object to Long
	private Long asLong(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).longValue();
		try {
			return Long.valueOf(o.toString());
		} catch (Exception e) {
			return null;
		}
	}

	// ═══════════════════════════════════════════════════════
	// DETAILED RESOURCE REPORT
	// Sheet layout:
	//   [0] Demand Summary  — one row per demand with level counts
	//   [1] Pivot Summary   — grouped demand×stage breakdown with outline grouping
	//   [2] Charts          — clustered bar + pie chart (visible data)
	//   [3] MasterData      — raw candidate rows
	// ═══════════════════════════════════════════════════════

	@Override
	@Transactional(readOnly = true)
	public byte[] generateDetailedResourceReport(DetailedResourceReportRequest req) throws Exception {
		if (req.getUserId() == null) {
			throw new IllegalArgumentException("userId is required for permission check.");
		}
		UserAccount user = userAccountRepo.findById(req.getUserId())
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + req.getUserId()));

		Pageable unpaged = Pageable.unpaged(Sort.by(Sort.Direction.DESC, "demandopendt"));
		Page<GroupFlowDto> pageResult = getDemandFlowList(user.getCompanyId(), req.getAccountId(), null, null,
				req.getFromDate(), req.getToDate(), unpaged);

		List<GroupFlowDto> allRows = pageResult.getContent();

		if (req.getDemandIds() != null && !req.getDemandIds().isEmpty()) {
			Set<Long> idSet = new java.util.HashSet<>(req.getDemandIds());
			allRows = allRows.stream().filter(r -> idSet.contains(r.getGroupId())).toList();
		}

		Map<Long, List<GroupFlowDto>> byGroup = allRows.stream()
				.collect(Collectors.groupingBy(GroupFlowDto::getGroupId, LinkedHashMap::new, Collectors.toList()));

		Map<Long, String> skillNameMap = skillRepo.findAll().stream()
				.collect(Collectors.toMap(Skill::getSkillId, Skill::getSkillName, (s1, s2) -> s1));

		Set<Long> demandIdSet = byGroup.keySet();
		Map<Long, List<Long>> demandSkillIds = demandRepo.findAllById(demandIdSet).stream()
				.collect(Collectors.toMap(Demand::getDemandid,
						d -> d.getSkillIds() != null ? d.getSkillIds() : List.of(), (l1, l2) -> l1));

		// ── Build flat master rows ──
		List<MasterDataRow> masterRows = new ArrayList<>();
		for (Map.Entry<Long, List<GroupFlowDto>> ge : byGroup.entrySet()) {
			Long demandId = ge.getKey();
			List<GroupFlowDto> groupRows = ge.getValue();
			if (groupRows.isEmpty()) continue;

			List<Long> skillIds = demandSkillIds.getOrDefault(demandId, List.of());
			String skillsStr = skillIds.stream()
					.map(id -> skillNameMap.getOrDefault(id, "Skill#" + id))
					.collect(Collectors.joining(", "));
			if (skillsStr.isBlank()) skillsStr = "-";

			Map<Long, List<GroupFlowDto>> byInterview = groupRows.stream()
					.filter(r -> r.getInterviewId() != null)
					.collect(Collectors.groupingBy(GroupFlowDto::getInterviewId,
							LinkedHashMap::new, Collectors.toList()));

			for (List<GroupFlowDto> ivRows : byInterview.values()) {
				GroupFlowDto iv0 = ivRows.get(0);
				MasterDataRow mRow = new MasterDataRow();
				mRow.setClient(nvl(iv0.getAccountName(), nvl(iv0.getCompanyName(), "-")));
				mRow.setSkill(skillsStr);
				mRow.setCandidateName(nvl(iv0.getCandidateName(), "-"));
				mRow.setStatus(determineCandidateStatus(ivRows));
				mRow.setContactNo(nvl(iv0.getCandidatePhoneNumber(), "-"));
				mRow.setEmail(nvl(iv0.getCandidateEmail(), "-"));
				mRow.setProject(nvl(iv0.getProjectName(), "-"));
				mRow.setDemandId("DEM-" + iv0.getGroupId());
				masterRows.add(mRow);
			}
		}

		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			Font boldFont = workbook.createFont();
			boldFont.setBold(true);
			boldFont.setFontHeightInPoints((short) 10);
			Font plainFont = workbook.createFont();
			plainFont.setFontHeightInPoints((short) 10);
			Font whiteBoldFont = workbook.createFont();
			whiteBoldFont.setBold(true);
			whiteBoldFont.setColor(IndexedColors.WHITE.getIndex());
			whiteBoldFont.setFontHeightInPoints((short) 10);

			XSSFCellStyle navyHdrStyle = workbook.createCellStyle();
			navyHdrStyle.setFont(whiteBoldFont);
			navyHdrStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(31, 73, 125), null));
			navyHdrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			navyHdrStyle.setBorderBottom(BorderStyle.THIN); navyHdrStyle.setBorderTop(BorderStyle.THIN);
			navyHdrStyle.setBorderLeft(BorderStyle.THIN);  navyHdrStyle.setBorderRight(BorderStyle.THIN);
			navyHdrStyle.setAlignment(HorizontalAlignment.LEFT);
			navyHdrStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle pivotHdrStyle = workbook.createCellStyle();
			pivotHdrStyle.setFont(whiteBoldFont);
			pivotHdrStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(68, 84, 106), null));
			pivotHdrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			pivotHdrStyle.setBorderBottom(BorderStyle.THIN); pivotHdrStyle.setBorderTop(BorderStyle.THIN);
			pivotHdrStyle.setBorderLeft(BorderStyle.THIN);  pivotHdrStyle.setBorderRight(BorderStyle.THIN);
			pivotHdrStyle.setAlignment(HorizontalAlignment.LEFT);
			pivotHdrStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle cellStyle = workbook.createCellStyle();
			cellStyle.setFont(plainFont);
			cellStyle.setBorderBottom(BorderStyle.THIN); cellStyle.setBorderTop(BorderStyle.THIN);
			cellStyle.setBorderLeft(BorderStyle.THIN);  cellStyle.setBorderRight(BorderStyle.THIN);
			cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle centerStyle = workbook.createCellStyle();
			centerStyle.cloneStyleFrom(cellStyle);
			centerStyle.setAlignment(HorizontalAlignment.CENTER);

			// Alternate-row style (light grey stripe)
			XSSFCellStyle altRowStyle = workbook.createCellStyle();
			altRowStyle.cloneStyleFrom(cellStyle);
			altRowStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(242, 242, 242), null));
			altRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle altCenterStyle = workbook.createCellStyle();
			altCenterStyle.cloneStyleFrom(altRowStyle);
			altCenterStyle.setAlignment(HorizontalAlignment.CENTER);

			XSSFCellStyle subtotalStyle = workbook.createCellStyle();
			subtotalStyle.cloneStyleFrom(cellStyle);
			subtotalStyle.setFont(boldFont);
			subtotalStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(218, 234, 250), null));
			subtotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			subtotalStyle.setBorderBottom(BorderStyle.MEDIUM);

			XSSFCellStyle subtotalCenterStyle = workbook.createCellStyle();
			subtotalCenterStyle.cloneStyleFrom(subtotalStyle);
			subtotalCenterStyle.setAlignment(HorizontalAlignment.CENTER);

			XSSFCellStyle grandTotalStyle = workbook.createCellStyle();
			grandTotalStyle.cloneStyleFrom(cellStyle);
			grandTotalStyle.setFont(boldFont);
			grandTotalStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(189, 215, 238), null));
			grandTotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			grandTotalStyle.setBorderBottom(BorderStyle.DOUBLE);

			XSSFCellStyle grandTotalCenterStyle = workbook.createCellStyle();
			grandTotalCenterStyle.cloneStyleFrom(grandTotalStyle);
			grandTotalCenterStyle.setAlignment(HorizontalAlignment.CENTER);

			// ── SHEET 0: Demand Summary ─────────────────────────────────────────
			// One row per demand.
			// Columns: Demand ID | Client | Skill(s) |
			//   L1 | L2 | L3 | HR Round | Final Round   (total at each level)
			//   | Allocated | Not Started | Total Candidates
			// ────────────────────────────────────────────────────────────────────
			final String[] LEVEL_COLS = {"L1", "L2", "L3", "HR Round", "Final Round"};
			// 3 fixed + levels + Allocated + Not Started + Total = 3 + 5 + 3 = 11
			final int totalCols = 3 + LEVEL_COLS.length + 3;
			final int COL_ALLOC    = 3 + LEVEL_COLS.length;
			final int COL_NOTSTART = 3 + LEVEL_COLS.length + 1;
			final int COL_TOTAL    = 3 + LEVEL_COLS.length + 2;

			XSSFSheet pivotSheet = workbook.createSheet("Demand Summary");
			pivotSheet.setDisplayGridlines(true);
			pivotSheet.setColumnWidth(0, 4000);
			pivotSheet.setColumnWidth(1, 5000);
			pivotSheet.setColumnWidth(2, 8000);
			for (int i = 3; i < totalCols; i++) pivotSheet.setColumnWidth(i, 3800);

			// Title
			Row rTitle = pivotSheet.createRow(0);
			rTitle.setHeightInPoints(30);
			Cell cTitle = rTitle.createCell(0);
			cTitle.setCellValue("Demand-wise Candidate Pipeline Summary");
			XSSFFont titleFont = workbook.createFont();
			titleFont.setBold(true);
			titleFont.setFontHeightInPoints((short) 14);
			titleFont.setColor(new XSSFColor(new java.awt.Color(31, 73, 125), null));
			XSSFCellStyle titleStyle = workbook.createCellStyle();
			titleStyle.setFont(titleFont);
			titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			cTitle.setCellStyle(titleStyle);
			pivotSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));

			// Subtitle
			Row rSub = pivotSheet.createRow(1);
			rSub.setHeightInPoints(16);
			Cell cSub = rSub.createCell(0);
			cSub.setCellValue("Each level column = total candidates at that stage (Scheduled + Selected + Rejected)  |  See Charts sheet for graphs");
			XSSFCellStyle subStyle2 = workbook.createCellStyle();
			XSSFFont subFont2 = workbook.createFont();
			subFont2.setItalic(true);
			subFont2.setFontHeightInPoints((short) 9);
			subFont2.setColor(new XSSFColor(new java.awt.Color(128, 128, 128), null));
			subStyle2.setFont(subFont2);
			cSub.setCellStyle(subStyle2);
			pivotSheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalCols - 1));

			// Spacer
			pivotSheet.createRow(2).setHeightInPoints(6);

			// Column headers
			Row rHead = pivotSheet.createRow(3);
			rHead.setHeightInPoints(28);
			setCellHelper(rHead, 0, "Demand ID",    pivotHdrStyle);
			setCellHelper(rHead, 1, "Client",       pivotHdrStyle);
			setCellHelper(rHead, 2, "Skill(s)",     pivotHdrStyle);
			for (int i = 0; i < LEVEL_COLS.length; i++) {
				setCellHelper(rHead, 3 + i, LEVEL_COLS[i], pivotHdrStyle);
			}
			setCellHelper(rHead, COL_ALLOC,    "Allocated",   pivotHdrStyle);
			setCellHelper(rHead, COL_NOTSTART, "Not Started", pivotHdrStyle);
			setCellHelper(rHead, COL_TOTAL,    "Total",       pivotHdrStyle);

			// Group masterRows by Demand ID
			Map<String, List<MasterDataRow>> rowsByDemand = new LinkedHashMap<>();
			for (MasterDataRow m : masterRows) {
				rowsByDemand.computeIfAbsent(m.getDemandId(), k -> new ArrayList<>()).add(m);
			}

			// Aggregation totals for chart
			int grandTotal = 0;
			int grandAlloc = 0;
			Map<String, Integer> grandByLevel = new LinkedHashMap<>();
			for (String lv : LEVEL_COLS) grandByLevel.put(lv, 0);

			// Demand-level chart data: demandId → level counts
			List<String>              chartDemands    = new ArrayList<>();
			List<Map<String, Integer>> chartLevelData = new ArrayList<>();
			List<Integer>             chartAllocData  = new ArrayList<>();

			int pivRowIdx = 4;
			for (Map.Entry<String, List<MasterDataRow>> demEntry : rowsByDemand.entrySet()) {
				String demandId         = demEntry.getKey();
				List<MasterDataRow> demRows = demEntry.getValue();
				MasterDataRow first     = demRows.get(0);

				int total      = demRows.size();
				int allocated  = 0;
				int notStarted = 0;
				Map<String, Integer> byLevel = new LinkedHashMap<>();
				for (String lv : LEVEL_COLS) byLevel.put(lv, 0);

				for (MasterDataRow m : demRows) {
					String status = m.getStatus();
					String lvl    = extractLevelFromStatus(status);
					if (lvl != null) {
						byLevel.merge(lvl, 1, Integer::sum);
					} else {
						// Pending / no level
						notStarted++;
					}
					// Allocated = candidated with "Selected" / "Allocated" (final outcome)
					if (isAllocatedStatus(status)) allocated++;
				}

				// Accumulate grand totals
				grandTotal += total;
				grandAlloc += allocated;
				for (String lv : LEVEL_COLS) {
					grandByLevel.merge(lv, byLevel.getOrDefault(lv, 0), Integer::sum);
				}

				// Store for chart
				chartDemands.add(demandId);
				chartLevelData.add(byLevel);
				chartAllocData.add(allocated);

				// Alternating row shading
				XSSFCellStyle rcs = (pivRowIdx % 2 == 0) ? cellStyle    : altRowStyle;
				XSSFCellStyle rcc = (pivRowIdx % 2 == 0) ? centerStyle  : altCenterStyle;

				Row row = pivotSheet.createRow(pivRowIdx++);
				row.setHeightInPoints(20);
				setCellHelper(row, 0, demandId,          rcs);
				setCellHelper(row, 1, first.getClient(), rcs);
				setCellHelper(row, 2, first.getSkill(),  rcs);
				for (int i = 0; i < LEVEL_COLS.length; i++) {
					Cell c = row.createCell(3 + i);
					c.setCellValue(byLevel.getOrDefault(LEVEL_COLS[i], 0));
					c.setCellStyle(rcc);
				}
				Cell ca = row.createCell(COL_ALLOC);
				ca.setCellValue(allocated);
				ca.setCellStyle(rcc);
				Cell cn = row.createCell(COL_NOTSTART);
				cn.setCellValue(notStarted);
				cn.setCellStyle(rcc);
				Cell ct = row.createCell(COL_TOTAL);
				ct.setCellValue(total);
				ct.setCellStyle(rcc);
			}

			// Grand Total row
			Row rGT = pivotSheet.createRow(pivRowIdx);
			rGT.setHeightInPoints(22);
			setCellHelper(rGT, 0, "Grand Total", grandTotalStyle);
			setCellHelper(rGT, 1, "",            grandTotalStyle);
			setCellHelper(rGT, 2, "",            grandTotalStyle);
			for (int i = 0; i < LEVEL_COLS.length; i++) {
				Cell c = rGT.createCell(3 + i);
				c.setCellValue(grandByLevel.getOrDefault(LEVEL_COLS[i], 0));
				c.setCellStyle(grandTotalCenterStyle);
			}
			Cell cGA = rGT.createCell(COL_ALLOC);
			cGA.setCellValue(grandAlloc);
			cGA.setCellStyle(grandTotalCenterStyle);
			Cell cGNS = rGT.createCell(COL_NOTSTART);
			cGNS.setCellValue(grandTotal - grandAlloc);
			cGNS.setCellStyle(grandTotalCenterStyle);
			Cell cGT2 = rGT.createCell(COL_TOTAL);
			cGT2.setCellValue(grandTotal);
			cGT2.setCellStyle(grandTotalCenterStyle);

			autoSizeColumnsHelper(pivotSheet, totalCols);

			// ── SHEET 1: Pivot Summary ─────────────────────────────────────────
			// Grouped by Demand/Skill, sub-rows = Stage + Status, with counts
			// ────────────────────────────────────────────────────────────────────
			XSSFSheet pivSumSheet = workbook.createSheet("Pivot Summary");
			pivSumSheet.setDisplayGridlines(true);

			// Styles for Pivot Summary
			XSSFCellStyle pvGroupHdrStyle = workbook.createCellStyle();
			pvGroupHdrStyle.setFont(whiteBoldFont);
			pvGroupHdrStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(31, 56, 100), null)); // #1F3864
			pvGroupHdrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			pvGroupHdrStyle.setBorderBottom(BorderStyle.THIN); pvGroupHdrStyle.setBorderTop(BorderStyle.THIN);
			pvGroupHdrStyle.setBorderLeft(BorderStyle.THIN);  pvGroupHdrStyle.setBorderRight(BorderStyle.THIN);
			pvGroupHdrStyle.setAlignment(HorizontalAlignment.LEFT);
			pvGroupHdrStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle pvSubRowStyle = workbook.createCellStyle();
			pvSubRowStyle.setFont(plainFont);
			pvSubRowStyle.setBorderBottom(BorderStyle.THIN); pvSubRowStyle.setBorderTop(BorderStyle.THIN);
			pvSubRowStyle.setBorderLeft(BorderStyle.THIN);  pvSubRowStyle.setBorderRight(BorderStyle.THIN);
			pvSubRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);
			pvSubRowStyle.setIndention((short) 2);

			XSSFCellStyle pvSubRowAltStyle = workbook.createCellStyle();
			pvSubRowAltStyle.cloneStyleFrom(pvSubRowStyle);
			pvSubRowAltStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(245, 245, 245), null));
			pvSubRowAltStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle pvCountStyle = workbook.createCellStyle();
			pvCountStyle.setFont(plainFont);
			pvCountStyle.setBorderBottom(BorderStyle.THIN); pvCountStyle.setBorderTop(BorderStyle.THIN);
			pvCountStyle.setBorderLeft(BorderStyle.THIN);  pvCountStyle.setBorderRight(BorderStyle.THIN);
			pvCountStyle.setAlignment(HorizontalAlignment.CENTER);
			pvCountStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle pvCountAltStyle = workbook.createCellStyle();
			pvCountAltStyle.cloneStyleFrom(pvCountStyle);
			pvCountAltStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(245, 245, 245), null));
			pvCountAltStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle pvSubtotalStyle = workbook.createCellStyle();
			pvSubtotalStyle.setFont(boldFont);
			pvSubtotalStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(189, 215, 238), null)); // #BDD7EE
			pvSubtotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			pvSubtotalStyle.setBorderBottom(BorderStyle.MEDIUM); pvSubtotalStyle.setBorderTop(BorderStyle.THIN);
			pvSubtotalStyle.setBorderLeft(BorderStyle.THIN);   pvSubtotalStyle.setBorderRight(BorderStyle.THIN);
			pvSubtotalStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle pvSubtotalCountStyle = workbook.createCellStyle();
			pvSubtotalCountStyle.cloneStyleFrom(pvSubtotalStyle);
			pvSubtotalCountStyle.setAlignment(HorizontalAlignment.CENTER);

			XSSFCellStyle pvGrandStyle = workbook.createCellStyle();
			pvGrandStyle.setFont(whiteBoldFont);
			pvGrandStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(46, 64, 87), null)); // #2E4057
			pvGrandStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			pvGrandStyle.setBorderBottom(BorderStyle.DOUBLE); pvGrandStyle.setBorderTop(BorderStyle.MEDIUM);
			pvGrandStyle.setBorderLeft(BorderStyle.THIN);    pvGrandStyle.setBorderRight(BorderStyle.THIN);
			pvGrandStyle.setVerticalAlignment(VerticalAlignment.CENTER);

			XSSFCellStyle pvGrandCountStyle = workbook.createCellStyle();
			pvGrandCountStyle.cloneStyleFrom(pvGrandStyle);
			pvGrandCountStyle.setAlignment(HorizontalAlignment.CENTER);

			// Column widths: A = ~280px, B = ~200px, C = ~100px
			pivSumSheet.setColumnWidth(0, 11000);  // ~280px
			pivSumSheet.setColumnWidth(1, 7800);   // ~200px
			pivSumSheet.setColumnWidth(2, 3900);   // ~100px

			// Title row
			int pvRow = 0;
			Row pvTitleRow = pivSumSheet.createRow(pvRow);
			pvTitleRow.setHeightInPoints(30);
			Cell pvTitleCell = pvTitleRow.createCell(0);
			pvTitleCell.setCellValue("Pivot Summary — Demand × Stage Breakdown");
			pvTitleCell.setCellStyle(titleStyle);
			pivSumSheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
			pvRow++;

			// Subtitle
			Row pvSubRow = pivSumSheet.createRow(pvRow);
			pvSubRow.setHeightInPoints(16);
			Cell pvSubCell = pvSubRow.createCell(0);
			pvSubCell.setCellValue("Grouped by Demand + Skill → Interview Stage & Status → Candidate Count");
			pvSubCell.setCellStyle(subStyle2);
			pivSumSheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));
			pvRow++;

			// Spacer
			pivSumSheet.createRow(pvRow).setHeightInPoints(6);
			pvRow++;

			// Header row (row 3) — freeze below this
			Row pvHdrRow = pivSumSheet.createRow(pvRow);
			pvHdrRow.setHeightInPoints(26);
			setCellHelper(pvHdrRow, 0, "Demand / Skill", pivotHdrStyle);
			setCellHelper(pvHdrRow, 1, "Interview Stage & Status", pivotHdrStyle);
			setCellHelper(pvHdrRow, 2, "Count", pivotHdrStyle);
			pvRow++;

			// Freeze header row
			pivSumSheet.createFreezePane(0, pvRow);

			// AutoFilter on header
			pivSumSheet.setAutoFilter(new CellRangeAddress(pvRow - 1, pvRow - 1, 0, 2));

			// Build pivot data: group masterRows by DemandId, then by status
			int pvGrandTotal = 0;
			// Track sub-row ranges for outline grouping
			List<int[]> outlineRanges = new ArrayList<>();

			for (Map.Entry<String, List<MasterDataRow>> demEntry : rowsByDemand.entrySet()) {
				String demandId = demEntry.getKey();
				List<MasterDataRow> demRows = demEntry.getValue();
				if (demRows.isEmpty()) continue;

				MasterDataRow first = demRows.get(0);
				String groupLabel = "\u229E " + demandId.replace("DEM-", "Demand -")
						+ " (" + first.getClient() + ") — " + first.getSkill();

				// Group header row
				Row gRow = pivSumSheet.createRow(pvRow);
				gRow.setHeightInPoints(24);
				setCellHelper(gRow, 0, groupLabel, pvGroupHdrStyle);
				setCellHelper(gRow, 1, "", pvGroupHdrStyle);
				setCellHelper(gRow, 2, "", pvGroupHdrStyle);
				// Merge group header across all columns
				pivSumSheet.addMergedRegion(new CellRangeAddress(pvRow, pvRow, 0, 2));
				int groupHeaderRow = pvRow;
				pvRow++;

				// Count by stage+status
				Map<String, Integer> stageCounts = new LinkedHashMap<>();
				for (MasterDataRow m : demRows) {
					String st = m.getStatus() != null && !m.getStatus().isBlank() ? m.getStatus() : "Pending";
					stageCounts.merge(st, 1, Integer::sum);
				}

				int subRowStart = pvRow;
				int subIdx = 0;
				int demSubTotal = 0;
				for (Map.Entry<String, Integer> se : stageCounts.entrySet()) {
					boolean isAlt = (subIdx % 2 == 1);
					XSSFCellStyle rowSt = isAlt ? pvSubRowAltStyle : pvSubRowStyle;
					XSSFCellStyle cntSt = isAlt ? pvCountAltStyle : pvCountStyle;

					Row sRow = pivSumSheet.createRow(pvRow);
					sRow.setHeightInPoints(20);
					setCellHelper(sRow, 0, "", rowSt);
					setCellHelper(sRow, 1, se.getKey(), rowSt);
					Cell cntCell = sRow.createCell(2);
					cntCell.setCellValue(se.getValue());
					cntCell.setCellStyle(cntSt);


					demSubTotal += se.getValue();
					pvRow++;
					subIdx++;
				}

				// Subtotal row
				Row stRow = pivSumSheet.createRow(pvRow);
				stRow.setHeightInPoints(22);
				String subLabel = "  " + demandId.replace("DEM-", "Demand -") + " Total";
				setCellHelper(stRow, 0, subLabel, pvSubtotalStyle);
				setCellHelper(stRow, 1, "", pvSubtotalStyle);
				Cell stCnt = stRow.createCell(2);
				stCnt.setCellValue(demSubTotal);
				stCnt.setCellStyle(pvSubtotalCountStyle);
				pvRow++;

				pvGrandTotal += demSubTotal;

				if (subRowStart < pvRow - 1) {
					outlineRanges.add(new int[]{subRowStart, pvRow - 2});
				}
			}

			// Grand Total row
			Row gTotRow = pivSumSheet.createRow(pvRow);
			gTotRow.setHeightInPoints(26);
			setCellHelper(gTotRow, 0, "Grand Total", pvGrandStyle);
			setCellHelper(gTotRow, 1, "", pvGrandStyle);
			Cell gTotCnt = gTotRow.createCell(2);
			gTotCnt.setCellValue(pvGrandTotal);
			gTotCnt.setCellStyle(pvGrandCountStyle);

			// Apply row grouping for collapsible +/- buttons
			for (int[] range : outlineRanges) {
				pivSumSheet.groupRow(range[0], range[1]);
			}
			pivSumSheet.setRowSumsBelow(true);

			// ── SHEET 2: Charts ──────────────────────────────────────────────────
			// Write helper data then create charts based on Demand Summary data.
			// ────────────────────────────────────────────────────────────────────
			XSSFSheet chartSheet = workbook.createSheet("Charts");
			chartSheet.setDisplayGridlines(false);

			// ── Chart data area: use visible rows, starting from row 0 ──
			// Columns: A=Demand ID, B..F=L1..Final Round, G=Allocated
			int chartCols = 1 + LEVEL_COLS.length + 1; // DemandID + levels + Allocated
			int chartDataStartRow = 0;

			// Header row
			Row chHdr = chartSheet.createRow(chartDataStartRow);
			chHdr.setHeightInPoints(18);
			setCellHelper(chHdr, 0, "Demand ID", navyHdrStyle);
			for (int i = 0; i < LEVEL_COLS.length; i++) {
				setCellHelper(chHdr, 1 + i, LEVEL_COLS[i], navyHdrStyle);
			}
			setCellHelper(chHdr, 1 + LEVEL_COLS.length, "Allocated", navyHdrStyle);

			int numDemands = chartDemands.size();
			for (int d = 0; d < numDemands; d++) {
				Row chRow = chartSheet.createRow(chartDataStartRow + 1 + d);
				chRow.setHeightInPoints(16);
				setCellHelper(chRow, 0, chartDemands.get(d), cellStyle);
				Map<String, Integer> dm = chartLevelData.get(d);
				for (int i = 0; i < LEVEL_COLS.length; i++) {
					Cell c = chRow.createCell(1 + i);
					c.setCellValue(dm.getOrDefault(LEVEL_COLS[i], 0));
					c.setCellStyle(centerStyle);
				}
				Cell ca2 = chRow.createCell(1 + LEVEL_COLS.length);
				ca2.setCellValue(chartAllocData.get(d));
				ca2.setCellStyle(centerStyle);
			}

			// Auto-size data columns
			for (int i = 0; i < chartCols; i++) {
				chartSheet.autoSizeColumn(i);
				if (chartSheet.getColumnWidth(i) < 3000) chartSheet.setColumnWidth(i, 3000);
			}

			// ── Chart 1: Clustered column chart — pipeline per demand ──
			if (numDemands > 0) {
				XSSFDrawing drawing1 = chartSheet.createDrawingPatriarch();
				// Place chart to the right of data — starts at column chartCols+1
				int chartLeftCol = chartCols + 1;
				XSSFClientAnchor anchor1 = drawing1.createAnchor(0, 0, 0, 0, chartLeftCol, 0, chartLeftCol + 14, Math.min(numDemands + 5, 25));
				XSSFChart chart1 = drawing1.createChart(anchor1);
				chart1.setTitleText("Candidate Pipeline by Demand");
				chart1.setTitleOverlay(false);

				XDDFCategoryAxis catAxis1 = chart1.createCategoryAxis(AxisPosition.BOTTOM);
				catAxis1.setTitle("Demand ID");
				XDDFValueAxis valAxis1 = chart1.createValueAxis(AxisPosition.LEFT);
				valAxis1.setTitle("Candidates");

				XDDFDataSource<String> demCats = XDDFDataSourcesFactory.fromStringCellRange(
						chartSheet, new CellRangeAddress(1, numDemands, 0, 0));

				XDDFChartData barData1 = chart1.createData(ChartTypes.BAR, catAxis1, valAxis1);
				if (barData1 instanceof XDDFBarChartData) {
					((XDDFBarChartData) barData1).setBarDirection(BarDirection.COL);
					((XDDFBarChartData) barData1).setBarGrouping(BarGrouping.CLUSTERED);
				}

				// One series per level + Allocated
				String[] chartSeriesNames = new String[LEVEL_COLS.length + 1];
				System.arraycopy(LEVEL_COLS, 0, chartSeriesNames, 0, LEVEL_COLS.length);
				chartSeriesNames[LEVEL_COLS.length] = "Allocated";

				for (int s = 0; s < chartSeriesNames.length; s++) {
					XDDFNumericalDataSource<Double> serVals = XDDFDataSourcesFactory.fromNumericCellRange(
							chartSheet, new CellRangeAddress(1, numDemands, 1 + s, 1 + s));
					XDDFChartData.Series ser = barData1.addSeries(demCats, serVals);
					ser.setTitle(chartSeriesNames[s], null);
				}
				chart1.plot(barData1);
			}

			// ── Pie chart helper data (below bar chart data) ──
			int pieDataRow = numDemands + 3;
			Row pieHdr = chartSheet.createRow(pieDataRow);
			pieHdr.setHeightInPoints(18);
			setCellHelper(pieHdr, 0, "Status",    navyHdrStyle);
			setCellHelper(pieHdr, 1, "Count",     navyHdrStyle);
			Row pieR1 = chartSheet.createRow(pieDataRow + 1);
			setCellHelper(pieR1, 0, "Allocated",  cellStyle);
			Cell pieV1 = pieR1.createCell(1);
			pieV1.setCellValue(grandAlloc);
			pieV1.setCellStyle(centerStyle);
			Row pieR2 = chartSheet.createRow(pieDataRow + 2);
			setCellHelper(pieR2, 0, "In Progress / Pending", cellStyle);
			Cell pieV2 = pieR2.createCell(1);
			pieV2.setCellValue(grandTotal - grandAlloc);
			pieV2.setCellStyle(centerStyle);

			// ── Chart 2: Pie chart — Allocation status ──
			if (grandTotal > 0) {
				XSSFDrawing drawing2 = chartSheet.createDrawingPatriarch();
				int chartLeftCol = chartCols + 1;
				int pieTopRow = Math.min(numDemands + 5, 25) + 1;
				XSSFClientAnchor anchor2 = drawing2.createAnchor(0, 0, 0, 0, chartLeftCol, pieTopRow, chartLeftCol + 10, pieTopRow + 15);
				XSSFChart chart2 = drawing2.createChart(anchor2);
				chart2.setTitleText("Overall Allocation Status");
				chart2.setTitleOverlay(false);

				XDDFDataSource<String> pieCats = XDDFDataSourcesFactory.fromStringCellRange(
						chartSheet, new CellRangeAddress(pieDataRow + 1, pieDataRow + 2, 0, 0));
				XDDFNumericalDataSource<Double> pieVals = XDDFDataSourcesFactory.fromNumericCellRange(
						chartSheet, new CellRangeAddress(pieDataRow + 1, pieDataRow + 2, 1, 1));
				XDDFChartData pieData = chart2.createData(ChartTypes.PIE, null, null);
				pieData.addSeries(pieCats, pieVals);
				chart2.plot(pieData);
			}

			// ── SHEET 3: MasterData (raw rows) ──────────────────────────────────
			XSSFSheet masterSheet = workbook.createSheet("MasterData");
			masterSheet.setDisplayGridlines(true);

			String[] mHeaders = { "Client", "Skill", "Candidate Name", "Status", "Contact No", "Email", "Project", "Demand ID" };
			Row rMHead = masterSheet.createRow(0);
			rMHead.setHeightInPoints(22);
			for (int i = 0; i < mHeaders.length; i++) {
				Cell c = rMHead.createCell(i);
				c.setCellValue(mHeaders[i]);
				c.setCellStyle(navyHdrStyle);
			}
			int mRowIdx = 1;
			for (MasterDataRow m : masterRows) {
				Row r = masterSheet.createRow(mRowIdx++);
				r.setHeightInPoints(18);
				String[] mVals = {
						m.getClient(), m.getSkill(), m.getCandidateName(), m.getStatus(),
						m.getContactNo(), m.getEmail(), m.getProject(), m.getDemandId()
				};
				for (int i = 0; i < mVals.length; i++) {
					Cell c = r.createCell(i);
					c.setCellValue(mVals[i] != null ? mVals[i] : "-");
					c.setCellStyle(cellStyle);
				}
			}
			autoSizeColumnsHelper(masterSheet, mHeaders.length);

			// Sheet 0 (Demand Summary) is active
			workbook.setActiveSheet(0);
			workbook.setSelectedTab(0);

			workbook.write(out);
			return out.toByteArray();
		}
	}

	

	private static class MasterDataRow {
		private String client;
		private String skill;
		private String candidateName;
		private String status;
		private String contactNo;
		private String email;
		private String project;
		private String demandId;

		public String getClient() { return client; }
		public void setClient(String client) { this.client = client; }
		public String getSkill() { return skill; }
		public void setSkill(String skill) { this.skill = skill; }
		public String getCandidateName() { return candidateName; }
		public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
		public String getStatus() { return status; }
		public void setStatus(String status) { this.status = status; }
		public String getContactNo() { return contactNo; }
		public void setContactNo(String contactNo) { this.contactNo = contactNo; }
		public String getEmail() { return email; }
		public void setEmail(String email) { this.email = email; }
		public String getProject() { return project; }
		public void setProject(String project) { this.project = project; }
		public String getDemandId() { return demandId; }
		public void setDemandId(String demandId) { this.demandId = demandId; }
	}

	public static String determineCandidateStatus(List<GroupFlowDto> rows) {
		if (rows == null || rows.isEmpty()) {
			return "Pending";
		}
		GroupFlowDto first = rows.get(0);
		String overallStatus = first.getInterviewOverallStatus();

		String resumeStatus = first.getCandidateResumeStatus();
		if (resumeStatus != null && (resumeStatus.equalsIgnoreCase("Drop") || resumeStatus.equalsIgnoreCase("Duplicate"))) {
			return capitalize(resumeStatus);
		}

		GroupFlowDto latestLvlRow = null;
		for (GroupFlowDto row : rows) {
			if (row.getInterviewLevel() != null && !row.getInterviewLevel().isBlank()) {
				if (latestLvlRow == null) {
					latestLvlRow = row;
				} else {
					if (row.getInterviewCompletedAt() != null) {
						if (latestLvlRow.getInterviewCompletedAt() == null || row.getInterviewCompletedAt().isAfter(latestLvlRow.getInterviewCompletedAt())) {
							latestLvlRow = row;
						}
					} else if (latestLvlRow.getInterviewCompletedAt() == null) {
						if (row.getInterviewLevelStatus() != null && latestLvlRow.getInterviewLevelStatus() == null) {
							latestLvlRow = row;
						}
					}
				}
			}
		}

		if (latestLvlRow != null) {
			String lvl = latestLvlRow.getInterviewLevel();
			String status = latestLvlRow.getInterviewLevelStatus();
			if (status == null || status.isBlank()) {
				status = "Pending";
			}

			lvl = capitalize(lvl);
			status = capitalize(status);

			if (status.equalsIgnoreCase("Drop") || status.equalsIgnoreCase("Duplicate")) {
				return status;
			}
			return lvl + " " + status;
		}

		if (overallStatus != null && !overallStatus.isBlank()) {
			return capitalize(overallStatus);
		}

		return "Pending";
	}

	private static String capitalize(String s) {
		if (s == null || s.isBlank()) return "";
		s = s.trim();
		if (s.length() == 1) return s.toUpperCase();
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}

	private void setCellHelper(Row row, int col, String value, CellStyle style) {
		Cell cell = row.createCell(col);
		cell.setCellValue(value != null ? value : "-");
		cell.setCellStyle(style);
	}

	private void autoSizeColumnsHelper(Sheet sheet, int colCount) {
		for (int i = 0; i < colCount; i++) {
			sheet.autoSizeColumn(i);
			if (sheet.getColumnWidth(i) > 12000) sheet.setColumnWidth(i, 12000);
			if (sheet.getColumnWidth(i) < 3000) sheet.setColumnWidth(i, 3000);
		}
	}

	/**
	 * Extracts the interview level prefix from a candidate status string.
	 * E.g. "L1 Selected" → "L1", "HR Round Scheduled" → "HR Round", "Pending" → null
	 */
	private static String extractLevelFromStatus(String status) {
		if (status == null || status.isBlank()) return null;
		String upper = status.trim().toUpperCase();
		if (upper.startsWith("L1"))    return "L1";
		if (upper.startsWith("L2"))    return "L2";
		if (upper.startsWith("L3"))    return "L3";
		if (upper.startsWith("HR"))    return "HR Round";
		if (upper.startsWith("FINAL")) return "Final Round";
		return null;
	}

	/**
	 * Returns true if the candidate's status indicates a rejection/drop at any level.
	 * Matches "Rejected", "Drop", "Duplicate" (case-insensitive).
	 */
	private static boolean isRejectedStatus(String status) {
		if (status == null) return false;
		String upper = status.toUpperCase();
		return upper.contains("REJECT") || upper.contains("DROP") || upper.contains("DUPLICATE");
	}

	/**
	 * Returns true if the candidate has been finally allocated/selected.
	 * "Selected" alone (no level prefix) or containing "Allocated" means they made it.
	 */
	private static boolean isAllocatedStatus(String status) {
		if (status == null) return false;
		String trimmed = status.trim();
		String upper   = trimmed.toUpperCase();
		// Pure "Selected" (not "L1 Selected" – that just means they passed L1)
		if (upper.equals("SELECTED") || upper.equals("ALLOCATED")) return true;
		// "Final Round Selected" counts as allocated
		if (upper.startsWith("FINAL") && upper.contains("SELECT")) return true;
		// Anything explicitly containing "Allocated"
		if (upper.contains("ALLOCATED")) return true;
		return false;
	}
}
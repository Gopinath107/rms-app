package com.ris.rms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.dto.EmployeeDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Department;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;
import com.ris.rms.portal.InternalResourcingPolicy;
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
import com.ris.rms.service.impl.EmployeeServiceImpl;

class ResourceImportTest {

	@TempDir
	Path tempDir;

	@Test
	void templateMarksMandatoryColumnsAndIncludesInstructions() throws Exception {
		DepartmentRepository deptRepo = mock(DepartmentRepository.class);
		Department d = new Department();
		d.setDepartmentId(5L);
		d.setDepartmentName("Veeva vault");
		when(deptRepo.findAllByCompanyId(1L)).thenReturn(List.of(d));

		ResourceImportTemplateService templateService = new ResourceImportTemplateService(deptRepo);
		byte[] bytes = templateService.generateTemplate("internal", 1L);

		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			assertThat(wb.getNumberOfSheets()).isEqualTo(2);
			assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Employees");
			assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("Instructions");

			Row header = wb.getSheetAt(0).getRow(0);
			assertThat((int) header.getLastCellNum()).isEqualTo(7);
			assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Name");
			assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Email");
			assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Role");
			assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Status");
			assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Experience");
			assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Phone Number");
			assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Resume Path");
			assertThat(header.getCell(0).getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			assertThat(header.getCell(0).getCellStyle().getFillForegroundColor())
					.isEqualTo(IndexedColors.LIGHT_ORANGE.getIndex());
		}

		byte[] candidateBytes = templateService.generateTemplate("external", 1L);
		try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(candidateBytes))) {
			assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Candidates");
			Row header = wb.getSheetAt(0).getRow(0);
			assertThat((int) header.getLastCellNum()).isEqualTo(7);
			assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Name");
			assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Resume Path");
		}
	}

	@Test
	void resumeResolverHandlesQuotedLocalPathsAndRejectsInvalidOnes() throws Exception {
		Path pdf = tempDir.resolve("sample_resume.pdf");
		Files.writeString(pdf, "%PDF-1.4 dummy");

		MultipartFile mf = ImportResumeResolver.resolve("\"" + pdf.toAbsolutePath() + "\"");
		assertThat(mf.getOriginalFilename()).isEqualTo("sample_resume.pdf");
		assertThat(mf.getContentType()).isEqualTo("application/pdf");
		assertThat(mf.getSize()).isGreaterThan(0);

		Path txt = tempDir.resolve("notes.txt");
		Files.writeString(txt, "plain text");
		assertThatThrownBy(() -> ImportResumeResolver.resolve(txt.toString()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported resume file type");

		assertThatThrownBy(() -> ImportResumeResolver.resolve(tempDir.resolve("missing.pdf").toString()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Resume file not found");
	}

	@Test
	void importEmployeesSupportsResumePathAndOverrideByEmail() throws Exception {
		EmployeeRepository repo = mock(EmployeeRepository.class);
		DepartmentRepository deptRepo = mock(DepartmentRepository.class);
		AccountRepository accountRepo = mock(AccountRepository.class);
		EmployeeDocumentRepository docRepo = mock(EmployeeDocumentRepository.class);
		EmployeeService self = mock(EmployeeService.class);

		EmployeeServiceImpl service = new EmployeeServiceImpl(
				repo,
				mock(InternalResourcingPolicy.class),
				mock(CompanyRepository.class),
				deptRepo,
				mock(SkillRepository.class),
				mock(EmployeeSkillRepository.class),
				docRepo,
				mock(ResumeStorageService.class),
				mock(AllocationRepository.class),
				mock(ProjectRepository.class),
				accountRepo,
				mock(StatusMasterRepository.class),
				mock(UserAccountRepository.class),
				mock(ResReqGroupRepository.class),
				mock(EmailService.class),
				mock(ResourceRequestRepository.class),
				mock(DemandRepository.class),
				mock(DemandService.class));
		ReflectionTestUtils.setField(service, "self", self);

		Department dept = new Department();
		dept.setDepartmentId(10L);
		dept.setDepartmentName("Software Engineer");
		when(deptRepo.findByCompanyIdAndDepartmentNameIgnoreCase(1L, "Software Engineer"))
				.thenReturn(Optional.of(dept));

		Path resumeDocx = tempDir.resolve("candidate.docx");
		Files.write(resumeDocx, new byte[] { 0x50, 0x4B, 0x03, 0x04 });

		Employee existingEmp = new Employee();
		existingEmp.setEmployeeId(99L);
		existingEmp.setCompanyId(1L);
		existingEmp.setEmail("existing@example.com");

		when(repo.findByCompanyIdAndEmailIgnoreCase(1L, "newuser@example.com")).thenReturn(Optional.empty());
		when(repo.findByCompanyIdAndEmailIgnoreCase(1L, "existing@example.com")).thenReturn(Optional.of(existingEmp));
		when(docRepo.findPrimaryResume(99L)).thenReturn(Optional.of(new EmployeeDocument()));

		byte[] workbookBytes;
		try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = wb.createSheet("Employees");
			Row h = s.createRow(0);
			String[] headers = { "Name", "Email", "Role", "Status", "Experience", "Phone Number", "Resume Path" };
			for (int i = 0; i < headers.length; i++) {
				h.createCell(i).setCellValue(headers[i]);
			}

			Row r1 = s.createRow(1);
			r1.createCell(0).setCellValue("Aarav Sharma");
			r1.createCell(1).setCellValue("newuser@example.com");
			r1.createCell(2).setCellValue("Software Engineer");
			r1.createCell(3).setCellValue("Bench");
			r1.createCell(4).setCellValue("4");
			r1.createCell(5).setCellValue("9876543210");
			r1.createCell(6).setCellValue(resumeDocx.toAbsolutePath().toString());

			Row r2 = s.createRow(2);
			r2.createCell(0).setCellValue("Priya Rao");
			r2.createCell(1).setCellValue("existing@example.com");
			r2.createCell(2).setCellValue("Software Engineer");
			r2.createCell(3).setCellValue("Billable");
			r2.createCell(4).setCellValue("6");
			r2.createCell(5).setCellValue("9876500001");
			r2.createCell(6).setCellValue("");

			wb.write(out);
			workbookBytes = out.toByteArray();
		}

		ImportResultDto overrideResult = service.importEmployees(1L, new ByteArrayInputStream(workbookBytes),
				"employees.xlsx", true);
		assertThat(overrideResult.getFailureCount()).isZero();
		assertThat(overrideResult.getCreatedCount()).isEqualTo(1);
		assertThat(overrideResult.getUpdatedCount()).isEqualTo(1);
		assertThat(overrideResult.getSuccessCount()).isEqualTo(2);
		verify(self).create(any(EmployeeDto.class), any(MultipartFile.class), eq(null), eq(null));
		verify(self).update(eq(99L), any(EmployeeDto.class), eq(null), eq(null), eq(null));

		ImportResultDto strictResult = service.importEmployees(1L, new ByteArrayInputStream(workbookBytes),
				"employees.xlsx", false);
		assertThat(strictResult.getCreatedCount()).isEqualTo(1);
		assertThat(strictResult.getUpdatedCount()).isZero();
		assertThat(strictResult.getFailureCount()).isEqualTo(1);
		assertThat(strictResult.getErrors().getFirst()).contains("already exists");
	}

	@Test
	void importEmployeesUpdatesExistingResourceWhenEmailChangesAndPhoneMatches() throws Exception {
		EmployeeRepository repo = mock(EmployeeRepository.class);
		CompanyRepository companyRepo = mock(CompanyRepository.class);
		DepartmentRepository deptRepo = mock(DepartmentRepository.class);
		AccountRepository accountRepo = mock(AccountRepository.class);
		EmployeeDocumentRepository docRepo = mock(EmployeeDocumentRepository.class);
		EmployeeService self = mock(EmployeeService.class);

		EmployeeServiceImpl service = new EmployeeServiceImpl(
				repo,
				mock(InternalResourcingPolicy.class),
				companyRepo,
				deptRepo,
				mock(SkillRepository.class),
				mock(EmployeeSkillRepository.class),
				docRepo,
				mock(ResumeStorageService.class),
				mock(AllocationRepository.class),
				mock(ProjectRepository.class),
				accountRepo,
				mock(StatusMasterRepository.class),
				mock(UserAccountRepository.class),
				mock(ResReqGroupRepository.class),
				mock(EmailService.class),
				mock(ResourceRequestRepository.class),
				mock(DemandRepository.class),
				mock(DemandService.class));
		ReflectionTestUtils.setField(service, "self", self);

		Department dept = new Department();
		dept.setDepartmentId(10L);
		dept.setCompanyId(1L);
		dept.setDepartmentName("Software Developer");
		when(deptRepo.findByCompanyIdAndDepartmentNameIgnoreCase(1L, "Software Developer"))
				.thenReturn(Optional.of(dept));
		when(deptRepo.findById(10L)).thenReturn(Optional.of(dept));

		Employee existingHema = new Employee();
		existingHema.setEmployeeId(55L);
		existingHema.setCompanyId(1L);
		existingHema.setFirstName("Hema");
		existingHema.setLastName("");
		existingHema.setEmail("hema@gmail.com");
		existingHema.setPhoneNumber("9887656678");
		existingHema.setPrimaryContactNo("9887656678");
		existingHema.setDepartmentId(10L);

		when(repo.findByCompanyIdAndEmailIgnoreCase(1L, "hema123@gmail.com")).thenReturn(Optional.empty());
		when(repo.findAllByCompanyId(1L)).thenReturn(List.of(existingHema));
		when(docRepo.findPrimaryResume(55L)).thenReturn(Optional.of(new EmployeeDocument()));
		when(repo.findById(55L)).thenReturn(Optional.of(existingHema));
		when(repo.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
		Company c = new Company();
		c.setCompanyId(1L);
		c.setCompanyName("Rudhra info solutions");
		when(companyRepo.findById(1L)).thenReturn(Optional.of(c));

		byte[] workbookBytes;
		try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet s = wb.createSheet("Employees");
			Row h = s.createRow(0);
			String[] headers = { "Name", "Email", "Role", "Status", "Experience", "Phone Number", "Resume Path" };
			for (int i = 0; i < headers.length; i++) {
				h.createCell(i).setCellValue(headers[i]);
			}

			Row r1 = s.createRow(1);
			r1.createCell(0).setCellValue("Hema");
			r1.createCell(1).setCellValue("hema123@gmail.com");
			r1.createCell(2).setCellValue("Software Developer");
			r1.createCell(3).setCellValue("Bench");
			r1.createCell(4).setCellValue("4");
			r1.createCell(5).setCellValue("9887656678");
			r1.createCell(6).setCellValue("");

			wb.write(out);
			workbookBytes = out.toByteArray();
		}

		ImportResultDto result = service.importEmployees(1L, new ByteArrayInputStream(workbookBytes),
				"employees.xlsx", true);
		assertThat(result.getFailureCount()).isZero();
		assertThat(result.getCreatedCount()).isZero();
		assertThat(result.getUpdatedCount()).isEqualTo(1);
		verify(self, never()).create(any(EmployeeDto.class), any(), any(), any());
		verify(self).update(eq(55L), any(EmployeeDto.class), eq(null), eq(null), eq(null));

		// Also verify EmployeeServiceImpl.update persists the new email on the matched employee
		EmployeeDto updateDto = new EmployeeDto();
		updateDto.setEmail("hema123@gmail.com");
		service.update(55L, updateDto, null, null, null);
		assertThat(existingHema.getEmail()).isEqualTo("hema123@gmail.com");
	}
}


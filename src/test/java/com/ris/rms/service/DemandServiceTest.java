package com.ris.rms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.ris.rms.dto.DemandCreateDto;
import com.ris.rms.dto.DemandResponseDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Department;
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
import com.ris.rms.service.impl.DemandServiceImpl;

class DemandServiceTest {

	private DemandRepository demandRepo;
	private ResourceRequestService rrService;
	private ResourceRequestRepository rrRepo;
	private CompanyRepository companyRepo;
	private AccountRepository accountRepo;
	private DepartmentRepository departmentRepo;
	private UserAccountRepository userAccountRepo;
	private EmployeeRepository employeeRepo;
	private SkillRepository skillRepo;
	private InterviewRepository interviewRepo;
	private AllocationRepository allocationRepo;
	private EmployeeDocumentRepository employeeDocumentRepo;
	private EmailService emailService;
	private CandidateRepository candidateRepo;
	private CandidateDocumentRepository candidateDocumentRepo;

	private DemandServiceImpl demandService;

	@BeforeEach
	void setUp() {
		demandRepo = mock(DemandRepository.class);
		rrService = mock(ResourceRequestService.class);
		rrRepo = mock(ResourceRequestRepository.class);
		companyRepo = mock(CompanyRepository.class);
		accountRepo = mock(AccountRepository.class);
		departmentRepo = mock(DepartmentRepository.class);
		userAccountRepo = mock(UserAccountRepository.class);
		employeeRepo = mock(EmployeeRepository.class);
		skillRepo = mock(SkillRepository.class);
		interviewRepo = mock(InterviewRepository.class);
		allocationRepo = mock(AllocationRepository.class);
		employeeDocumentRepo = mock(EmployeeDocumentRepository.class);
		emailService = mock(EmailService.class);
		candidateRepo = mock(CandidateRepository.class);
		candidateDocumentRepo = mock(CandidateDocumentRepository.class);

		demandService = new DemandServiceImpl(
				demandRepo,
				rrService,
				rrRepo,
				companyRepo,
				accountRepo,
				departmentRepo,
				userAccountRepo,
				employeeRepo,
				skillRepo,
				interviewRepo,
				allocationRepo,
				employeeDocumentRepo,
				emailService,
				candidateRepo,
				candidateDocumentRepo);

		Company company = new Company();
		company.setCompanyId(1L);
		company.setCompanyName("Rudhra Info Solutions");
		when(companyRepo.findById(1L)).thenReturn(Optional.of(company));

		Account clientA = new Account();
		clientA.setAccountId(10L);
		clientA.setAccountName("Client A");
		when(accountRepo.findById(10L)).thenReturn(Optional.of(clientA));

		Account clientB = new Account();
		clientB.setAccountId(20L);
		clientB.setAccountName("Client B");
		when(accountRepo.findById(20L)).thenReturn(Optional.of(clientB));

		Department dept = new Department();
		dept.setDepartmentId(5L);
		dept.setDepartmentName("Engineering");
		when(departmentRepo.findById(5L)).thenReturn(Optional.of(dept));

		UserAccount user = new UserAccount();
		user.setUserId(99L);
		when(userAccountRepo.findById(99L)).thenReturn(Optional.of(user));
	}

	@Test
	void createDemand_allowsSameDemandTitleForDifferentClients() {
		// Mock count returns 0 (no existing demand for this specific client)
		when(demandRepo.count(any(Specification.class))).thenReturn(0L);

		Demand savedDemand = new Demand();
		savedDemand.setDemandid(101L);
		savedDemand.setCompanyId(1L);
		savedDemand.setAccountId(20L); // Client B
		savedDemand.setDepartmentId(5L);
		savedDemand.setDemandtitle("Full Stack Developer");
		savedDemand.setDemandopendt(LocalDate.now());
		savedDemand.setOverallStatus("Open");
		savedDemand.setResourceRequestsCount(2);

		when(demandRepo.save(any(Demand.class))).thenReturn(savedDemand);
		when(demandRepo.findById(101L)).thenReturn(Optional.of(savedDemand));

		DemandCreateDto dto = new DemandCreateDto();
		dto.setCompanyId(1L);
		dto.setAccountId(20L); // Client B
		dto.setDepartmentId(5L);
		dto.setRequesterUserId(99L);
		dto.setDemandTitle("Full Stack Developer"); // Same title as existing demand in Client A
		dto.setResourceRequests(2);

		DemandResponseDto response = demandService.createDemand(dto);

		assertThat(response).isNotNull();
		assertThat(response.getDemandid()).isEqualTo(101L);
		assertThat(response.getDemandTitle()).isEqualTo("Full Stack Developer");
		assertThat(response.getAccountId()).isEqualTo(20L);
		verify(demandRepo).save(any(Demand.class));
	}

	@Test
	void createDemand_blocksDuplicateDemandTitleForSameClient() {
		// Mock count returns 1 when checking duplicates for the same client and project
		when(demandRepo.count(any(Specification.class))).thenReturn(1L);

		DemandCreateDto dto = new DemandCreateDto();
		dto.setCompanyId(1L);
		dto.setAccountId(10L); // Client A
		dto.setDepartmentId(5L);
		dto.setRequesterUserId(99L);
		dto.setDemandTitle("Full Stack Developer");
		dto.setResourceRequests(1);

		assertThatThrownBy(() -> demandService.createDemand(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A Demand with this title already exists for this client and project. Please check the list.");
	}
}


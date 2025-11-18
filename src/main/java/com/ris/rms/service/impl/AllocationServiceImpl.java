package com.ris.rms.service.impl;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ris.rms.dto.AllocationDto;
import com.ris.rms.entity.Allocation;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Department;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.Notification;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.DemandRepository;
import com.ris.rms.repository.DepartmentRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.NotificationRepository;
import com.ris.rms.repository.ProjectRepository;
import com.ris.rms.repository.ResourceRequestRepository;
import com.ris.rms.service.AllocationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AllocationServiceImpl implements AllocationService {

	private final AllocationRepository repo;
	private final ProjectRepository projectRepo;
	private final EmployeeRepository employeeRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final ResourceRequestRepository rrRepo;
	private final NotificationRepository notificationRepo;
	private final DemandRepository demandRepo;
	private final DepartmentRepository departmentRepo;
	private static final java.util.Set<String> ALLOWED_ALLOCATION_STATUS = java.util.Set.of("Client", "Internal");

	@Override
	public AllocationDto create(AllocationDto dto) {

		if (dto.getEmployeeId() == null)
			throw new IllegalArgumentException("employeeId is required");
		if (dto.getRequestId() == null)
			throw new IllegalArgumentException("requestId is required");
		if (dto.getStartDate() == null)
			throw new IllegalArgumentException("startDate is required");

		if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate()))
			throw new IllegalArgumentException("endDate must be on/after startDate");

		ResourceRequest rr = rrRepo.findById(dto.getRequestId())
				.orElseThrow(() -> new IllegalArgumentException("ResourceRequest not found: " + dto.getRequestId()));

		Employee e = employeeRepo.findById(dto.getEmployeeId())
				.orElseThrow(() -> new IllegalArgumentException("Employee not found"));

		Project p = null;
		Long companyId = null;
		String projectNameForNotification = null;
		Demand d = null;

		if (rr.getProjectId() != null) {
			p = projectRepo.findById(rr.getProjectId())
					.orElseThrow(() -> new IllegalArgumentException("Project not found: " + rr.getProjectId()));
			companyId = p.getCompanyId();
			projectNameForNotification = p.getProjectName();
		} else if (rr.getDemandId() != null) {

			d = demandRepo.findById(rr.getDemandId())
					.orElseThrow(() -> new IllegalArgumentException("Demand not found: " + rr.getDemandId()));
			companyId = d.getCompanyId();
			projectNameForNotification = d.getProjectName();
		} else {
			companyId = e.getCompanyId();
		}

		if (!Objects.equals(companyId, e.getCompanyId())) {
			throw new IllegalArgumentException("Employee and Request must belong to the same company");
		}
		String roleToSet = null;

		if (d != null && d.getDepartmentId() != null) {
			roleToSet = departmentRepo.findById(d.getDepartmentId()).map(Department::getDepartmentName).orElse(null);
		}

		if (roleToSet == null && e.getDepartmentId() != null) {
			roleToSet = departmentRepo.findById(e.getDepartmentId()).map(Department::getDepartmentName).orElse(null);
		}

		if (roleToSet == null) {
			roleToSet = "General";
		}
		Allocation a = new Allocation();
		a.setAllocationId(null);
		a.setProjectId(rr.getProjectId());
		a.setEmployeeId(dto.getEmployeeId());
		a.setRequestId(dto.getRequestId());

		a.setProjectRole(roleToSet);
		a.setIsBillable(Boolean.TRUE);
		a.setStatus("Client");

		a.setStartDate(dto.getStartDate());
		a.setEndDate(dto.getEndDate());

		Allocation saved = repo.save(a);

		reflectEmployeeStatusFromAllocation(saved);
		final String finalProjectName = projectNameForNotification;
		if (a.getRequestId() != null) {
			if (rr.getRequesterUserId() != null) {
				Notification n = new Notification();
				n.setUserId(rr.getRequesterUserId());
				n.setTitle("Resource Allocated");

				n.setMessage("Employee #" + e.getEmployeeId() + " allocated to project '"
						+ (finalProjectName != null ? finalProjectName : "") + "'.");
				n.setPriority("Normal");
				n.setRelatedEntityType("Allocation");
				n.setRelatedEntityId(saved.getAllocationId());
				notificationRepo.save(n);
			}
		}

		return enrich(toDto(saved));
	}

	@Override
	@Transactional(readOnly = true)
	public AllocationDto getById(Long id) {
		Allocation a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Allocation not found"));
		return enrich(toDto(a));
	}

	@Override
	@Transactional(readOnly = true)
	public List<AllocationDto> list(Long companyId, Long projectId, Long employeeId, String status, Boolean billable,
			Integer page, Integer size) {

		List<Allocation> base = (page != null && size != null && page >= 0 && size > 0)
				? repo.findAll(PageRequest.of(page, size)).getContent()
				: repo.findAll();

		return base.stream()

				.filter(a -> projectId == null || Objects.equals(a.getProjectId(), projectId))

				.filter(a -> employeeId == null || Objects.equals(a.getEmployeeId(), employeeId))

				.filter(a -> status == null || (a.getStatus() != null && a.getStatus().equalsIgnoreCase(status)))

				.filter(a -> billable == null || Objects.equals(a.getIsBillable(), billable))

				.filter(a -> {
					if (companyId == null) {
						return true;
					}

					if (a.getProjectId() != null) {
						return projectRepo.findById(a.getProjectId())
								.map(p -> Objects.equals(p.getCompanyId(), companyId)).orElse(false);
					}

					if (a.getRequestId() != null) {
						return rrRepo.findById(a.getRequestId()).flatMap(rr -> {
							if (rr.getDemandId() == null) {
								return Optional.<Demand>empty();
							}
							return demandRepo.findById(rr.getDemandId());
						}).map(d -> Objects.equals(d.getCompanyId(), companyId)).orElse(false);
					}

					return false;
				})
				.sorted(Comparator.comparing(Allocation::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::toDto).map(this::enrich).collect(Collectors.toList());
	}

	@Override
	public AllocationDto update(Long allocationId, AllocationDto dto) {
		Allocation existing = repo.findById(allocationId)
				.orElseThrow(() -> new IllegalArgumentException("Allocation not found"));

		LocalDate newStart = dto.getStartDate() != null ? dto.getStartDate() : existing.getStartDate();
		LocalDate newEnd = dto.getEndDate() != null ? dto.getEndDate() : existing.getEndDate();
		if (newEnd != null && newStart != null && newEnd.isBefore(newStart)) {
			throw new IllegalArgumentException("endDate must be on/after startDate");
		}

		if (dto.getProjectRole() != null)
			existing.setProjectRole(dto.getProjectRole());
		if (dto.getIsBillable() != null)
			existing.setIsBillable(dto.getIsBillable());
		if (dto.getStartDate() != null)
			existing.setStartDate(dto.getStartDate());
		if (dto.getEndDate() != null)
			existing.setEndDate(dto.getEndDate());

		if (dto.getStatus() != null) {
			if (!ALLOWED_ALLOCATION_STATUS.contains(dto.getStatus())) {
				throw new IllegalArgumentException("status must be one of " + ALLOWED_ALLOCATION_STATUS);
			}
			existing.setStatus(dto.getStatus());
		}

		Allocation saved = repo.save(existing);

		if (dto.getStatus() != null) {
			reflectEmployeeStatusFromAllocation(saved);
		}

		return enrich(toDto(saved));
	}

	@Override
	public void delete(Long allocationId) {
		if (!repo.existsById(allocationId))
			throw new IllegalArgumentException("Allocation not found");
		repo.deleteById(allocationId);

	}

	private void reflectEmployeeStatusFromAllocation(Allocation allocation) {
		if (allocation == null || allocation.getEmployeeId() == null)
			return;
		String st = allocation.getStatus();
		if (st == null || !ALLOWED_ALLOCATION_STATUS.contains(st))
			return;

		employeeRepo.findById(allocation.getEmployeeId()).ifPresent(emp -> {
			emp.setStatus(st);
			employeeRepo.save(emp);
		});
	}

	private AllocationDto toDto(Allocation a) {
		AllocationDto dto = new AllocationDto();
		dto.setAllocationId(a.getAllocationId());
		dto.setProjectId(a.getProjectId());
		dto.setEmployeeId(a.getEmployeeId());
		dto.setRequestId(a.getRequestId());
		dto.setProjectRole(a.getProjectRole());
		dto.setIsBillable(a.getIsBillable());
		dto.setStartDate(a.getStartDate());
		dto.setEndDate(a.getEndDate());
		dto.setStatus(a.getStatus());
		return dto;
	}

	private AllocationDto enrich(AllocationDto dto) {

		if (dto.getProjectId() != null) {
			projectRepo.findById(dto.getProjectId()).ifPresent(p -> {
				dto.setProjectId(p.getProjectId());
				dto.setProjectName(p.getProjectName());
				dto.setCompanyId(p.getCompanyId());
				companyRepo.findById(p.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
				if (p.getAccountId() != null) {
					accountRepo.findById(p.getAccountId()).ifPresent(a -> {
						dto.setAccountId(a.getAccountId());
						dto.setAccountName(a.getAccountName());
					});
				}
			});
		}

		if (dto.getProjectId() == null && dto.getRequestId() != null) {
			rrRepo.findById(dto.getRequestId()).ifPresent(rr -> {

				if (rr.getProjectId() != null) {
					projectRepo.findById(rr.getProjectId()).ifPresent(p -> {
						dto.setProjectId(p.getProjectId());
						dto.setProjectName(p.getProjectName());
						dto.setCompanyId(p.getCompanyId());
						companyRepo.findById(p.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));
						if (p.getAccountId() != null) {
							accountRepo.findById(p.getAccountId()).ifPresent(a -> {
								dto.setAccountId(a.getAccountId());
								dto.setAccountName(a.getAccountName());
							});
						}
					});
				}

				else if (rr.getDemandId() != null) {
					demandRepo.findById(rr.getDemandId()).ifPresent(d -> {

						dto.setProjectName(d.getProjectName());

						dto.setCompanyId(d.getCompanyId());
						companyRepo.findById(d.getCompanyId()).ifPresent(c -> dto.setCompanyName(c.getCompanyName()));

						if (d.getAccountId() != null) {
							accountRepo.findById(d.getAccountId()).ifPresent(a -> {
								dto.setAccountId(a.getAccountId());
								dto.setAccountName(a.getAccountName());
							});
						}
					});
				}
			});
		}

		if (dto.getEmployeeId() != null) {
			employeeRepo.findById(dto.getEmployeeId()).ifPresent(e -> {
				dto.setEmployeeName(e.getFirstName() + " " + e.getLastName());
			});
		}

		return dto;
	}

}
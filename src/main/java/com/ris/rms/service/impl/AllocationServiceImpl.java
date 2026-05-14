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
import com.ris.rms.entity.Candidate;
import com.ris.rms.entity.Demand;
import com.ris.rms.entity.Department;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.Notification;
import com.ris.rms.entity.Project;
import com.ris.rms.entity.ResourceRequest;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.AllocationRepository;
import com.ris.rms.repository.CandidateRepository;
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
	private final CandidateRepository candidateRepo;
	private final CompanyRepository companyRepo;
	private final AccountRepository accountRepo;
	private final ResourceRequestRepository rrRepo;
	private final NotificationRepository notificationRepo;
	private final DemandRepository demandRepo;
	private final DepartmentRepository departmentRepo;
	private static final java.util.Set<String> ALLOWED_ALLOCATION_STATUS = java.util.Set.of("Client", "Internal");

	@Override
	public AllocationDto create(AllocationDto dto) {

		if (dto.getRequestId() == null)
			throw new IllegalArgumentException("requestId is required");
		
		ResourceRequest rrCheck = rrRepo.findById(dto.getRequestId()).orElse(null);
        if (rrCheck != null && rrCheck.getDemandId() != null) {
            Demand d = demandRepo.findById(rrCheck.getDemandId()).orElse(null);
            if (d != null) {
                String status = d.getOverallStatus();
                
                if ("Hold".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException("Demand Id '" + d.getDemandid() + "'. status is '" + status + "'. We can't proceed. Please change the status to Open to continue.");
                }
            }
        }
        
		if (dto.getStartDate() == null)
			throw new IllegalArgumentException("startDate is required");

		if (dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate()))
			throw new IllegalArgumentException("endDate must be on/after startDate");

		boolean hasEmp = dto.getEmployeeId() != null;
		boolean hasCand = dto.getCandidateId() != null;

		if (!hasEmp && !hasCand)
			throw new IllegalArgumentException("Either employeeId or candidateId is required");
		if (hasEmp && hasCand)
			throw new IllegalArgumentException("Cannot allocate both employeeId and candidateId in one record");

		ResourceRequest rr = rrRepo.findById(dto.getRequestId())
				.orElseThrow(() -> new IllegalArgumentException("ResourceRequest not found: " + dto.getRequestId()));

		Employee emp = null;
		Candidate cand = null;
		Long entityCompanyId = null;
		Long entityIdForNotification = null;
		String entityType = null;
		Long entityDeptId = null;

		if (hasEmp) {
			emp = employeeRepo.findById(dto.getEmployeeId())
					.orElseThrow(() -> new IllegalArgumentException("Employee not found"));
			entityCompanyId = emp.getCompanyId();
			entityIdForNotification = emp.getEmployeeId();
			entityType = "Employee";
			entityDeptId = emp.getDepartmentId();
		} else {
			cand = candidateRepo.findById(dto.getCandidateId())
					.orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
			entityCompanyId = cand.getCompanyId();
			entityIdForNotification = cand.getCandidateId();
			entityType = "Candidate";
		}

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
			companyId = entityCompanyId;
		}

		if (!Objects.equals(companyId, entityCompanyId)) {
			throw new IllegalArgumentException(entityType + " and Request must belong to the same company");
		}

		Allocation duplicate = findDuplicateAllocation(dto.getRequestId(), dto.getEmployeeId(), dto.getCandidateId());
		if (duplicate != null) {
			return enrich(toDto(duplicate));
		}

		String roleToSet = null;

		if (d != null && d.getDepartmentId() != null) {
			roleToSet = departmentRepo.findById(d.getDepartmentId()).map(Department::getDepartmentName).orElse(null);
		}

		if (roleToSet == null && entityDeptId != null) {
			roleToSet = departmentRepo.findById(entityDeptId).map(Department::getDepartmentName).orElse(null);
		}

		if (roleToSet == null) {
			roleToSet = "General";
		}
		Allocation a = new Allocation();
		a.setAllocationId(null);
		a.setProjectId(rr.getProjectId());
		a.setEmployeeId(dto.getEmployeeId());
		a.setCandidateId(dto.getCandidateId());
		a.setRequestId(dto.getRequestId());

		a.setProjectRole(roleToSet);
		a.setIsBillable(Boolean.TRUE);
		a.setStatus("Client");

		a.setStartDate(dto.getStartDate());
		a.setEndDate(dto.getEndDate());

		Allocation saved = repo.save(a);

		if (hasEmp) {
			reflectEmployeeStatusFromAllocation(saved);
		} else {
			reflectCandidateStatusFromAllocation(saved);
		}

		final String finalProjectName = projectNameForNotification;
		final Long notificationTargetId = entityIdForNotification;
		final String notificationTargetType = entityType;

		if (a.getRequestId() != null) {
			if (rr.getRequesterUserId() != null) {
				Notification n = new Notification();
				n.setUserId(rr.getRequesterUserId());
				n.setTitle("Resource Allocated");

				n.setMessage(notificationTargetType + " #" + notificationTargetId + " allocated to project '"
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
	    public List<AllocationDto> list(Long companyId, Long projectId, Long employeeId, Long candidateId, 
	                                    String status, Boolean billable,
	                                    Integer page, Integer size) {

	        List<Allocation> base = (page != null && size != null && page >= 0 && size > 0)
	                ? repo.findAll(PageRequest.of(page, size)).getContent()
	                : repo.findAll();

	        return base.stream()
	                .filter(a -> projectId == null || Objects.equals(a.getProjectId(), projectId))
	                .filter(a -> employeeId == null || Objects.equals(a.getEmployeeId(), employeeId))
	                .filter(a -> candidateId == null || Objects.equals(a.getCandidateId(), candidateId)) // Added Candidate ID check
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

		if (existing.getRequestId() != null) {
            ResourceRequest rrCheck = rrRepo.findById(existing.getRequestId()).orElse(null);
            if (rrCheck != null && rrCheck.getDemandId() != null) {
                Demand d = demandRepo.findById(rrCheck.getDemandId()).orElse(null);
                if (d != null) {
                    String status = d.getOverallStatus();
                   
                    if ("Hold".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)) {
                        throw new IllegalArgumentException("Demand Id '" + d.getDemandid() + "'. status is '" + status + "'. We can't proceed. Please change the status to Open to continue.");
                    }
                }
            }
       }
		
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
			if (saved.getEmployeeId() != null) {
				reflectEmployeeStatusFromAllocation(saved);
			} else if (saved.getCandidateId() != null) {
				reflectCandidateStatusFromAllocation(saved);
			}
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

	private void reflectCandidateStatusFromAllocation(Allocation allocation) {
		if (allocation == null || allocation.getCandidateId() == null)
			return;

		candidateRepo.findById(allocation.getCandidateId()).ifPresent(c -> {
			c.setStatus("Selected");
			candidateRepo.save(c);
		});
	}

	private AllocationDto toDto(Allocation a) {
		AllocationDto dto = new AllocationDto();
		dto.setAllocationId(a.getAllocationId());
		dto.setProjectId(a.getProjectId());
		dto.setEmployeeId(a.getEmployeeId());
		dto.setCandidateId(a.getCandidateId());
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
		} else if (dto.getCandidateId() != null) {
			candidateRepo.findById(dto.getCandidateId()).ifPresent(c -> {
				dto.setCandidateName(c.getFullName());
			});
		}

		return dto;
	}

	private Allocation findDuplicateAllocation(Long requestId, Long employeeId, Long candidateId) {
		if (requestId == null)
			return null;

		List<Allocation> existingForRequest = repo.findByRequestIdIn(List.of(requestId));
		for (Allocation al : existingForRequest) {
			boolean sameEmp = employeeId != null && Objects.equals(al.getEmployeeId(), employeeId);
			boolean sameCand = candidateId != null && Objects.equals(al.getCandidateId(), candidateId);
			boolean activeStatus = al.getStatus() != null && ALLOWED_ALLOCATION_STATUS.contains(al.getStatus());
			if ((sameEmp || sameCand) && activeStatus) {
				return al;
			}
		}
		return null;
	}

}
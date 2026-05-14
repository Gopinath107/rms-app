package com.ris.rms.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ris.rms.entity.Company;
import com.ris.rms.entity.EmployeeDocument;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

	@Query("""
			    select d from EmployeeDocument d
			    where d.employeeId = :employeeId
			      and (d.documentType = 'resume' or d.documentType is null)
			    order by
			      coalesce(d.isPrimary, false) desc,
			      coalesce(d.uploadedAt, CURRENT_TIMESTAMP) desc
			""")
	List<EmployeeDocument> findAllResumes(Long employeeId);

	@Query("""
			    select d from EmployeeDocument d
			    where d.employeeId = :employeeId
			      and d.documentType = 'resume'
			      and d.isPrimary = true
			""")
	Optional<EmployeeDocument> findPrimaryResume(Long employeeId);

	Optional<EmployeeDocument> findTopByEmployeeIdOrderByDocumentIdDesc(Long employeeId);

	@Query("""
			    select d from EmployeeDocument d
			    where d.employeeId in :employeeIds
			      and d.documentType = 'resume'
			      and d.isPrimary = true
			""")
	List<EmployeeDocument> findPrimaryResumesForEmployees(List<Long> employeeIds);
}

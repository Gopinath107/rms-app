package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ris.rms.entity.Employee;
import com.ris.rms.entity.EmployeeDocument;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
	boolean existsByCompanyIdAndEmailIgnoreCase(Long companyId, String email);

	List<Employee> findAllByCompanyId(Long companyId);

	@Query(value = """
			    SELECT
			        al.employee_id,
			        p.project_name,
			        a.account_name AS client_name,
			        al.start_date,
			        al.end_date
			    FROM rms.allocation al
			    JOIN rms.project p      ON p.project_id = al.project_id
			    LEFT JOIN rms.account a ON a.account_id = p.account_id
			    WHERE al.employee_id IN (:empIds)
			    ORDER BY
			        al.employee_id,
			        COALESCE(al.end_date, DATE '9999-12-31') DESC,
			        al.start_date DESC
			""", nativeQuery = true)
	List<Object[]> findEmployeeProjectRows(@Param("empIds") List<Long> empIds);

	boolean existsByCompanyIdAndEmailIgnoreCaseAndEmployeeIdNot(Long companyId, String email, Long employeeId);

	boolean existsByCompanyIdAndPersonalemailidIgnoreCase(Long companyId, String personalEmailId);

	boolean existsByCompanyIdAndPersonalemailidIgnoreCaseAndEmployeeIdNot(Long companyId, String personalEmailId,
			Long employeeId);
}

package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
	boolean existsByCompanyIdAndDepartmentNameIgnoreCase(Long companyId, String departmentName);
	Optional<Department> findByCompanyIdAndDepartmentNameIgnoreCase(Long companyId, String departmentName);
	List<Department> findAllByCompanyId(Long companyId);
}

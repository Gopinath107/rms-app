package com.ris.rms.repository;

import com.ris.rms.entity.EmployeeSkill;
import com.ris.rms.entity.EmployeeSkillId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, EmployeeSkillId> {
	List<EmployeeSkill> findAllByEmployeeId(Long employeeId);

	void deleteAllByEmployeeId(Long employeeId);
}

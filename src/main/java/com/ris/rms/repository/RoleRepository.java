package com.ris.rms.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
	boolean existsByCompanyIdAndRoleNameIgnoreCase(Long companyId, String roleName);
    Optional<Role> findByCompanyIdAndRoleNameIgnoreCase(Long companyId, String roleName);

	List<Role> findAllByCompanyId(Long companyId);
}

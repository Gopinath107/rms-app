package com.ris.rms.repository;

import com.ris.rms.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	boolean existsByEmailIgnoreCase(String email);

	Optional<UserAccount> findByEmailIgnoreCase(String email);
	Optional<UserAccount> findByEmployeeId(Long employeeId);

	boolean existsByEmployeeId(Long employeeId);

	List<UserAccount> findAllByCompanyId(Long companyId);

	List<UserAccount> findAllByCompanyIdAndRoleId(Long companyId, Long roleId);
}

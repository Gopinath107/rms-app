package com.ris.rms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ris.rms.entity.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByCompanyIdAndAccountNameIgnoreCase(Long companyId, String accountName);
    Page<Account> findAllByCompanyId(Long companyId, Pageable pageable);

}

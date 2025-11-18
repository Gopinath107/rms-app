package com.ris.rms.repository;

import com.ris.rms.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
	boolean existsByCompanyNameIgnoreCase(String companyName);

	Optional<Company> findByCompanyNameIgnoreCase(String companyName);
}

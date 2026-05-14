package com.ris.rms.service.impl;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ris.rms.dto.AccountDto;
import com.ris.rms.entity.Account;
import com.ris.rms.entity.Company;
import com.ris.rms.repository.AccountRepository;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.service.AccountService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

	private final AccountRepository repo;
	private final CompanyRepository companyRepo;

	@Override
	public AccountDto create(AccountDto dto) {
		Company comp = companyRepo.findById(dto.getCompanyId())
				.orElseThrow(() -> new IllegalArgumentException("Company not found"));

		if (repo.existsByCompanyIdAndAccountNameIgnoreCase(dto.getCompanyId(), dto.getAccountName())) {
			throw new IllegalArgumentException("Account name already exists for this company");
		}

		Account entity = toEntity(dto);
		entity.setAccountId(null);
		entity.setRelationshipEndDate(null);

		if (entity.getRelationshipStartDate() == null) {
			entity.setRelationshipStartDate(LocalDate.now());
		}
		if (dto.getStatus() == null || dto.getStatus().isBlank()) {
			entity.setStatus("Active");
		} else if (!dto.getStatus().equalsIgnoreCase("Active") && !dto.getStatus().equalsIgnoreCase("Inactive")) {
			throw new IllegalArgumentException("status must be 'Active' or 'Inactive'");
		} else {
			entity.setStatus(cap(dto.getStatus()));
		}
		Account saved = repo.save(entity);
		AccountDto out = toDto(saved, comp.getCompanyName());
		out.setRelationshipEndDate(null);
		return out;
	}

	@Override
	@Transactional(readOnly = true)
	public AccountDto getById(Long id) {
		Account a = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));
		String companyName = companyRepo.findById(a.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(a, companyName);
	}

	@Override
	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<AccountDto> list(Long companyId, String q, Integer page, Integer size) {
		PageRequest pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 10);

		Page<Account> accountPage;
		if (companyId != null) {
			accountPage = repo.findAllByCompanyId(companyId, pageable);
		} else {
			accountPage = repo.findAll(pageable);
		}

		return accountPage.map(a -> {
			String companyName = companyRepo.findById(a.getCompanyId()).map(Company::getCompanyName).orElse(null);
			return toDto(a, companyName);
		});
	}

	@Override
	public AccountDto update(Long id, AccountDto dto) {
		Account existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));

		if (dto.getCompanyId() != null && !dto.getCompanyId().equals(existing.getCompanyId())) {
			throw new IllegalArgumentException("companyId cannot be changed");
		}

		if (dto.getAccountName() != null && !dto.getAccountName().equalsIgnoreCase(existing.getAccountName())
				&& repo.existsByCompanyIdAndAccountNameIgnoreCase(existing.getCompanyId(), dto.getAccountName())) {
			throw new IllegalArgumentException("Account name already exists for this company");
		}

		if (dto.getStatus() != null) {
			String s = cap(dto.getStatus());
			if (!s.equals("Active") && !s.equals("Inactive")) {
				throw new IllegalArgumentException("status must be 'Active' or 'Inactive'");
			}
			existing.setStatus(s);
		}

		LocalDate start = dto.getRelationshipStartDate() != null ? dto.getRelationshipStartDate()
				: existing.getRelationshipStartDate();
		LocalDate end = dto.getRelationshipEndDate() != null ? dto.getRelationshipEndDate()
				: existing.getRelationshipEndDate();
		if (start != null && end != null && end.isBefore(start)) {
			throw new IllegalArgumentException("relationshipEndDate must be on/after relationshipStartDate");
		}

		if (dto.getAccountName() != null)
			existing.setAccountName(dto.getAccountName());
		if (dto.getIndustry() != null)
			existing.setIndustry(dto.getIndustry());
		if (dto.getContactPersonName() != null)
			existing.setContactPersonName(dto.getContactPersonName());
		if (dto.getContactPersonEmail() != null)
			existing.setContactPersonEmail(dto.getContactPersonEmail());
		if (dto.getRelationshipStartDate() != null)
			existing.setRelationshipStartDate(dto.getRelationshipStartDate());
		if (dto.getRelationshipEndDate() != null)
			existing.setRelationshipEndDate(dto.getRelationshipEndDate());

		Account saved = repo.save(existing);
		String companyName = companyRepo.findById(saved.getCompanyId()).map(Company::getCompanyName).orElse(null);
		return toDto(saved, companyName);
	}

	@Override
	public void delete(Long id) {
		if (!repo.existsById(id)) {
			throw new IllegalArgumentException("Account not found");
		}
		repo.deleteById(id);
	}

	private AccountDto toDto(Account a, String companyName) {
		AccountDto dto = new AccountDto();
		dto.setAccountId(a.getAccountId());
		dto.setCompanyId(a.getCompanyId());
		dto.setCompanyName(companyName);
		dto.setAccountName(a.getAccountName());
		dto.setIndustry(a.getIndustry());
		dto.setContactPersonName(a.getContactPersonName());
		dto.setContactPersonEmail(a.getContactPersonEmail());
		dto.setRelationshipStartDate(a.getRelationshipStartDate());
		dto.setRelationshipEndDate(a.getRelationshipEndDate());
		dto.setStatus(a.getStatus());
		return dto;
	}

	private Account toEntity(AccountDto dto) {
		Account a = new Account();
		a.setAccountId(dto.getAccountId());
		a.setCompanyId(dto.getCompanyId());
		a.setAccountName(dto.getAccountName());
		a.setIndustry(dto.getIndustry());
		a.setContactPersonName(dto.getContactPersonName());
		a.setContactPersonEmail(dto.getContactPersonEmail());
		a.setRelationshipStartDate(dto.getRelationshipStartDate());
		a.setStatus(dto.getStatus());
		return a;
	}

	private String cap(String v) {
		return v == null ? null : v.substring(0, 1).toUpperCase() + v.substring(1).toLowerCase();
	}

}

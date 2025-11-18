package com.ris.rms.service.impl;

import com.ris.rms.dto.AuthUserDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userRepo;
    private final CompanyRepository companyRepo;
    private final EmployeeRepository employeeRepo;
    private final RoleRepository roleRepo;

    @Override
    public AuthUserDto login(String email, String password) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("email and password are required");
        }

        UserAccount ua = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (ua.getIsActive() == null || !ua.getIsActive()) {
            throw new IllegalArgumentException("User is inactive");
        }

        // SIMPLE DB VALIDATION (no hashing yet): compare plain strings
        String stored = ua.getPasswordHash();
        if (stored == null || !stored.equals(password)) {
            // If you later store BCrypt hashes, replace with BCrypt check here
            throw new IllegalArgumentException("Invalid credentials");
        }

        AuthUserDto out = new AuthUserDto();
        out.setUserId(ua.getUserId());
        out.setCompanyId(ua.getCompanyId());
        out.setEmail(ua.getEmail());
        out.setIsActive(ua.getIsActive());

        companyRepo.findById(ua.getCompanyId()).map(Company::getCompanyName)
                .ifPresent(out::setCompanyName);

        if (ua.getEmployeeId() != null) {
            employeeRepo.findById(ua.getEmployeeId()).map(e -> e.getFirstName() + " " + e.getLastName())
                    .ifPresent(out::setEmployeeName);
            out.setEmployeeId(ua.getEmployeeId());
        }

        if (ua.getRoleId() != null) {
            roleRepo.findById(ua.getRoleId()).map(Role::getRoleName).ifPresent(out::setRoleName);
            out.setRoleId(ua.getRoleId());
        }

        return out;
    }
}

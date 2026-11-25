package com.ris.rms.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ris.rms.dto.AuthUserDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Employee;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.security.JwtUtil;
import com.ris.rms.security.PasswordHashUtil;
import com.ris.rms.service.AuthService;
import com.ris.rms.portal.RisPortalEmployeeService;
import com.ris.rms.portal.PortalAccessClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.ris.rms.entity.PasswordResetToken;
import com.ris.rms.repository.PasswordResetTokenRepository;
import com.ris.rms.service.EmailService;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

	private final UserAccountRepository userRepo;
	private final CompanyRepository companyRepo;
	private final EmployeeRepository employeeRepo;
	private final RoleRepository roleRepo;
	private final JwtUtil jwtUtil;
	private final PasswordHashUtil passwordHashUtil;
	private final PasswordResetTokenRepository passwordResetTokenRepo;
	private final EmailService emailService;
	private final PortalAccessClient portalAccess;
	private final RisPortalEmployeeService risPortalEmployeeService;

	@Override
	@Transactional(readOnly = false) // override class-level readOnly=true — login migrates password hash on write
	public AuthUserDto login(String email, String password, Long requestedRoleId) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			throw new IllegalArgumentException("email and password are required");
		}

		UserAccount ua = null;
		String authenticatedEmail = email.trim();

		// 1. Primary Authentication: Single Source Identity via RIS Portal
		if (risPortalEmployeeService != null) {
			Optional<RisPortalEmployeeService.PortalUserAuth> portalAuthOpt =
					risPortalEmployeeService.authenticate(email, password, passwordHashUtil);

			if (portalAuthOpt.isPresent()) {
				RisPortalEmployeeService.PortalUserAuth portalUser = portalAuthOpt.get();

				if (!portalUser.hasRmsAccess()) {
					throw new org.springframework.security.access.AccessDeniedException(
							"Your account is not authorized for RMS in RIS Portal. Please contact your administrator."
					);
				}

				authenticatedEmail = portalUser.email();
				ua = userRepo.findByEmailIgnoreCase(authenticatedEmail).orElse(null);

				Long rmsRoleId = mapPortalRoleToRmsRoleId(portalUser.roles());
				Long compId = portalUser.companyId() != null ? portalUser.companyId() : 1L;

				if (ua == null) {
					ua = new UserAccount();
					ua.setEmail(authenticatedEmail);
					ua.setName(portalUser.getFullName());
					ua.setCompanyId(compId);
					ua.setEmployeeId(portalUser.employeeId());
					ua.setIsActive(true);
					ua.setPasswordHash(portalUser.passwordHash());
					ua.setRoleIds(List.of(rmsRoleId));
					ua = userRepo.save(ua);
				} else {
					ua.setPasswordHash(portalUser.passwordHash());
					if (portalUser.employeeId() != null) ua.setEmployeeId(portalUser.employeeId());
					if (portalUser.companyId() != null) ua.setCompanyId(portalUser.companyId());
					if (portalUser.getFullName() != null && !portalUser.getFullName().isBlank()) {
						ua.setName(portalUser.getFullName());
					}
					if (ua.getRoleIds() == null || ua.getRoleIds().isEmpty()) {
						ua.setRoleIds(List.of(rmsRoleId));
					}
					ua = userRepo.save(ua);
				}

				// Ensure employee exists in rms.employee table for FK constraints
				Long empId = ua.getEmployeeId();
				if (empId != null && !employeeRepo.existsById(empId)) {
					String firstName = portalUser.firstName() != null ? portalUser.firstName() : "";
					String lastName = portalUser.lastName() != null ? portalUser.lastName() : "";
					employeeRepo.insertWithExplicitId(empId, compId, firstName, lastName, authenticatedEmail);
				}
			}
		}

		// 2. Fallback Authentication: Local RMS database (for backward-compatibility)
		if (ua == null) {
			ua = userRepo.findByEmailIgnoreCase(email)
					.orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

			if (ua.getIsActive() == null || !ua.getIsActive()) {
				throw new IllegalArgumentException("User is inactive");
			}

			String stored = ua.getPasswordHash();
			if (!passwordHashUtil.verifyPassword(password, stored)) {
				throw new IllegalArgumentException("Invalid credentials");
			}

			// BCrypt migration: if stored password is not BCrypt, re-hash and save on login
			if (stored != null && !stored.startsWith("$2")) {
				ua.setPasswordHash(passwordHashUtil.hashPassword(password));
				userRepo.save(ua);
			}
		}

		AuthUserDto out = new AuthUserDto();
		try {
			portalAccess.access(ua);
		} catch (Exception e) {
			log.debug("Portal access check: {}", e.getMessage());
		}

		out.setUserId(ua.getUserId());
		out.setCompanyId(ua.getCompanyId());
		out.setEmail(ua.getEmail());
		out.setIsActive(ua.getIsActive());

		companyRepo.findById(ua.getCompanyId()).map(Company::getCompanyName).ifPresent(out::setCompanyName);

		Long empId = ua.getEmployeeId();
		String empName = null;

		if (empId != null) {
			var opt = employeeRepo.findById(empId);
			if (opt.isPresent()) {
				empName = (opt.get().getFirstName() + " " + opt.get().getLastName()).trim();
			}
		}

		// If user_account does not have an employeeId yet, resolve from RIS Portal by email
		if (empId == null && risPortalEmployeeService != null) {
			var portalEmpOpt = risPortalEmployeeService.findEmployeeByEmail(ua.getEmail());
			if (portalEmpOpt.isPresent()) {
				var portalEmp = portalEmpOpt.get();
				empId = portalEmp.employeeId();
				empName = portalEmp.getFullName();

				if (!employeeRepo.existsById(empId)) {
					String firstName = portalEmp.firstName() != null ? portalEmp.firstName() : "";
					String lastName = portalEmp.lastName() != null ? portalEmp.lastName() : "";
					employeeRepo.insertWithExplicitId(empId, ua.getCompanyId(), firstName, lastName, portalEmp.email());
				}

				ua.setEmployeeId(empId);
				if (ua.getName() == null || ua.getName().isBlank()) {
					ua.setName(empName);
				}
				userRepo.save(ua);
			}
		}

		out.setEmployeeId(empId);
		out.setEmployeeName(empName != null && !empName.isBlank() ? empName : ua.getName());

		List<Long> myRoles = ua.getRoleIds();
		Long finalRoleId = null;

		if (myRoles == null || myRoles.isEmpty()) {
			if (requestedRoleId != null) {
				throw new IllegalArgumentException("User has no roles assigned");
			}
			finalRoleId = 3L; // Default PMO
		} else {
			if (requestedRoleId != null) {
				if (myRoles.contains(requestedRoleId)) {
					finalRoleId = requestedRoleId;
				} else {
					throw new IllegalArgumentException(
							"User is not authorized for the requested role ID: " + requestedRoleId);
				}
			} else {
				finalRoleId = myRoles.get(0);
			}
		}

		if (finalRoleId != null) {
			out.setRoleId(finalRoleId);
			roleRepo.findById(finalRoleId).map(Role::getRoleName).ifPresent(out::setRoleName);
		}

		String token = jwtUtil.generateToken(ua.getEmail(), ua.getEmail(), ua.getUserId());
		out.setToken(token);

		return out;
	}

	private Long mapPortalRoleToRmsRoleId(List<String> portalRoles) {
		if (portalRoles == null || portalRoles.isEmpty()) {
			return 3L; // PMO default
		}
		for (String r : portalRoles) {
			String upper = r.toUpperCase();
			if (upper.contains("SUPER_ADMIN") || upper.contains("IT_ADMIN") || upper.contains("SYSTEM_ADMIN")) {
				return 6L; // system-admin
			}
			if (upper.contains("HR")) {
				return 1L; // hr
			}
			if (upper.contains("PMO")) {
				return 3L; // pmo
			}
			if (upper.contains("PROJECT_MANAGER") || upper.contains("MANAGER")) {
				return 2L; // Project Manager
			}
			if (upper.contains("PORTFOLIO")) {
				return 4L; // portfolio-manager
			}
			if (upper.contains("SALES")) {
				return 5L; // sales-manager
			}
			if (upper.contains("INTERVIEW") || upper.contains("PANEL")) {
				return 7L; // interview-panel
			}
		}
		return 3L;
	}

	@Override
	@Transactional(readOnly = false) // override class-level readOnly=true
	public void generateAndSendOtp(String email) {
		UserAccount ua = userRepo.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalArgumentException("No account found with this email address."));

		if (ua.getIsActive() == null || !ua.getIsActive()) {
			throw new IllegalArgumentException("User is inactive");
		}

		String otp = String.format("%06d", new Random().nextInt(999999));
		PasswordResetToken token = new PasswordResetToken();
		token.setEmail(email.toLowerCase());

		token.setOtp(otp);
		token.setExpiryDate(LocalDateTime.now().plusSeconds(90));
		token.setIsUsed(false);

		passwordResetTokenRepo.save(token);

		boolean sent;
		try {
			sent = Boolean.TRUE.equals(emailService.sendPasswordResetOtpAsync(email, otp).get(15, TimeUnit.SECONDS));
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while sending OTP email.", ie);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to send OTP email.", ex);
		}
		if (!sent) {
			throw new IllegalStateException("Failed to send OTP email. Check SMTP configuration.");
		}
	}

	@Override
	public void verifyOtp(String email, String otp) {
		PasswordResetToken token = passwordResetTokenRepo.findByEmailIgnoreCaseAndOtpAndIsUsedFalse(email, otp)
				.orElseThrow(() -> new IllegalArgumentException("Invalid OTP."));

		if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("Expired OTP.");
		}
	}

	@Override
	@Transactional(readOnly = false) // override class-level readOnly=true — must write password to DB
	public void resetPassword(String email, String otp, String newPassword) {
		if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
			throw new IllegalArgumentException("Password must be at least 6 characters long.");
		}

		PasswordResetToken token = passwordResetTokenRepo.findByEmailIgnoreCaseAndOtpAndIsUsedFalse(email, otp)
				.orElseThrow(() -> new IllegalArgumentException("Invalid OTP."));

		if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("Expired OTP.");
		}

		UserAccount ua = userRepo.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalArgumentException("No account found with this email address."));

		String hashed = passwordHashUtil.hashPassword(newPassword);
		ua.setPasswordHash(hashed);
		userRepo.save(ua);

		token.setIsUsed(true);
		passwordResetTokenRepo.save(token);
	}
}

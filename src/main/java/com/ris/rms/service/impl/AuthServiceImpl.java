package com.ris.rms.service.impl;

import org.springframework.security.access.AccessDeniedException;
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
	@Transactional(readOnly = false)
	public AuthUserDto login(String email, String password, Long requestedRoleId) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			throw new IllegalArgumentException("email and password are required");
		}

		// Single-Source Identity: authenticate strictly against RIS Portal (hrms.user_account)
		RisPortalEmployeeService.PortalUserAuth portalUser = risPortalEmployeeService
				.authenticate(email, password, passwordHashUtil)
				.orElseThrow(() -> new IllegalArgumentException("Invalid RIS Portal credentials"));

		if (!portalUser.hasRmsAccess()) {
			throw new AccessDeniedException("Your RMS access has not been granted or was removed in RIS Portal");
		}

		String authenticatedEmail = portalUser.email();
		UserAccount ua = userRepo.findByEmailIgnoreCase(authenticatedEmail).orElse(null);

		Long portalCompId = portalUser.companyId();
		Long compId = (portalCompId != null && companyRepo.existsById(portalCompId)) ? portalCompId : 1L;
		List<Long> defaultRoles = resolveRmsRoles(portalUser);

		Long targetCompId = (ua != null && ua.getCompanyId() != null && companyRepo.existsById(ua.getCompanyId()))
				? ua.getCompanyId()
				: compId;

		// Ensure matching employee exists in rms.employee BEFORE saving user_account (satisfies fk_user_employee_company)
		Long targetEmpId = resolveOrCreateEmployee(targetCompId, portalUser, authenticatedEmail, ua != null ? ua.getEmployeeId() : null);

		if (ua == null) {
			ua = new UserAccount();
			ua.setEmail(authenticatedEmail);
			ua.setName(portalUser.getFullName());
			ua.setCompanyId(targetCompId);
			ua.setEmployeeId(targetEmpId);
			ua.setIsActive(true);
			ua.setPasswordHash(portalUser.passwordHash());
			ua.setRoleIds(defaultRoles);
			ua = userRepo.save(ua);
		} else {
			ua.setPasswordHash(portalUser.passwordHash());
			ua.setIsActive(true);
			ua.setCompanyId(targetCompId);
			if (targetEmpId != null) {
				ua.setEmployeeId(targetEmpId);
			}
			if (portalUser.getFullName() != null && !portalUser.getFullName().isBlank()) {
				ua.setName(portalUser.getFullName());
			}
			if (ua.getRoleIds() == null || ua.getRoleIds().isEmpty()) {
				ua.setRoleIds(defaultRoles);
			}
			ua = userRepo.save(ua);
		}

		// Verify portal access strictly — never swallow AccessDeniedException
		portalAccess.access(ua);

		AuthUserDto out = new AuthUserDto();
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

		out.setEmployeeId(empId);
		out.setEmployeeName(empName != null && !empName.isBlank() ? empName : ua.getName());

		List<Long> myRoles = ua.getRoleIds();
		Long finalRoleId = null;

		if (myRoles == null || myRoles.isEmpty()) {
			if (requestedRoleId != null) {
				throw new IllegalArgumentException("User has no roles assigned");
			}
			finalRoleId = 3L;
		} else if (requestedRoleId != null) {
			if (myRoles.contains(requestedRoleId)) {
				finalRoleId = requestedRoleId;
			} else {
				throw new IllegalArgumentException(
						"User is not authorized for the requested role ID: " + requestedRoleId);
			}
		} else {
			finalRoleId = myRoles.get(0);
		}

		if (finalRoleId != null) {
			out.setRoleId(finalRoleId);
			roleRepo.findById(finalRoleId).map(Role::getRoleName).ifPresent(out::setRoleName);
		}

		String token = jwtUtil.generateToken(ua.getEmail(), ua.getEmail(), ua.getUserId());
		out.setToken(token);

		return out;
	}

	private Long resolveOrCreateEmployee(
			Long companyId,
			RisPortalEmployeeService.PortalUserAuth portalUser,
			String email,
			Long existingUserEmpId) {
		var byEmail = employeeRepo.findByCompanyIdAndEmailIgnoreCase(companyId, email);
		if (byEmail.isPresent()) {
			return byEmail.get().getEmployeeId();
		}

		String firstName = portalUser.firstName() != null && !portalUser.firstName().isBlank()
				? portalUser.firstName().trim() : portalUser.getFullName();
		String lastName = portalUser.lastName() != null ? portalUser.lastName().trim() : "";

		Long portalEmpId = portalUser.employeeId();
		if (portalEmpId != null && !employeeRepo.existsById(portalEmpId)) {
			employeeRepo.insertWithExplicitId(portalEmpId, companyId, firstName, lastName, email);
			return portalEmpId;
		}

		if (existingUserEmpId != null && employeeRepo.findById(existingUserEmpId)
				.filter(e -> companyId.equals(e.getCompanyId())).isPresent()) {
			return existingUserEmpId;
		}

		if (portalEmpId != null && employeeRepo.findById(portalEmpId)
				.filter(e -> companyId.equals(e.getCompanyId())).isPresent()) {
			return portalEmpId;
		}

		Employee created = new Employee();
		created.setCompanyId(companyId);
		created.setFirstName(firstName);
		created.setLastName(lastName);
		created.setEmail(email);
		return employeeRepo.save(created).getEmployeeId();
	}

	private List<Long> resolveRmsRoles(RisPortalEmployeeService.PortalUserAuth portalUser) {
		if (portalUser.isSuperAdmin() || (portalUser.rmsPermissions() != null && portalUser.rmsPermissions().contains("RMS_ADMIN"))) {
			return List.of(6L, 1L, 2L, 3L, 4L, 5L, 7L);
		}
		return List.of(mapPortalRoleToRmsRoleId(portalUser.roles()));
	}

	private Long mapPortalRoleToRmsRoleId(List<String> portalRoles) {
		if (portalRoles == null || portalRoles.isEmpty()) {
			return 3L;
		}
		for (String r : portalRoles) {
			String upper = r.toUpperCase();
			if (upper.contains("SUPER_ADMIN") || upper.contains("IT_ADMIN") || upper.contains("SYSTEM_ADMIN")) {
				return 6L;
			}
			if (upper.contains("HR")) {
				return 1L;
			}
			if (upper.contains("PMO")) {
				return 3L;
			}
			if (upper.contains("PROJECT_MANAGER") || upper.contains("MANAGER")) {
				return 2L;
			}
			if (upper.contains("PORTFOLIO")) {
				return 4L;
			}
			if (upper.contains("SALES")) {
				return 5L;
			}
			if (upper.contains("INTERVIEW") || upper.contains("PANEL")) {
				return 7L;
			}
		}
		return 3L;
	}

	@Override
	@Transactional(readOnly = false)
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
	@Transactional(readOnly = false)
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

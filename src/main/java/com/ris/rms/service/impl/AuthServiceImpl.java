package com.ris.rms.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ris.rms.dto.AuthUserDto;
import com.ris.rms.entity.Company;
import com.ris.rms.entity.Role;
import com.ris.rms.entity.UserAccount;
import com.ris.rms.repository.CompanyRepository;
import com.ris.rms.repository.EmployeeRepository;
import com.ris.rms.repository.RoleRepository;
import com.ris.rms.repository.UserAccountRepository;
import com.ris.rms.security.JwtUtil;
import com.ris.rms.security.PasswordHashUtil;
import com.ris.rms.service.AuthService;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import com.ris.rms.entity.PasswordResetToken;
import com.ris.rms.repository.PasswordResetTokenRepository;
import com.ris.rms.service.EmailService;

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

	@Override
	public AuthUserDto login(String email, String password, Long requestedRoleId) {
		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			throw new IllegalArgumentException("email and password are required");
		}

		UserAccount ua = userRepo.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

		if (ua.getIsActive() == null || !ua.getIsActive()) {
			throw new IllegalArgumentException("User is inactive");
		}

		String stored = ua.getPasswordHash();
		if (!passwordHashUtil.verifyPassword(password, stored)) {
			throw new IllegalArgumentException("Invalid credentials");
		}

		AuthUserDto out = new AuthUserDto();
		out.setUserId(ua.getUserId());
		out.setCompanyId(ua.getCompanyId());
		out.setEmail(ua.getEmail());
		out.setIsActive(ua.getIsActive());

		companyRepo.findById(ua.getCompanyId()).map(Company::getCompanyName).ifPresent(out::setCompanyName);

		if (ua.getEmployeeId() != null) {
			employeeRepo.findById(ua.getEmployeeId()).map(e -> e.getFirstName() + " " + e.getLastName())
					.ifPresent(out::setEmployeeName);
			out.setEmployeeId(ua.getEmployeeId());
		}


		List<Long> myRoles = ua.getRoleIds(); 
		Long finalRoleId = null;

		if (myRoles == null || myRoles.isEmpty()) {

			if (requestedRoleId != null) {
				throw new IllegalArgumentException("User has no roles assigned");
			}
		} else {
			if (requestedRoleId != null) {
				if (myRoles.contains(requestedRoleId)) {
					finalRoleId = requestedRoleId;
				} else {
					throw new IllegalArgumentException("User is not authorized for the requested role ID: " + requestedRoleId);
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

	@Override
	@Transactional
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

		emailService.sendPasswordResetOtpAsync(email, otp);
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
	@Transactional
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

		String hashed = passwordHashUtil.hashPasswordSHA256(newPassword);
		ua.setPasswordHash(hashed);
		userRepo.save(ua);

		token.setIsUsed(true);
		passwordResetTokenRepo.save(token);
	}
}
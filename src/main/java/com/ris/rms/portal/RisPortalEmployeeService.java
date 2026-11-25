package com.ris.rms.portal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ris.rms.security.PasswordHashUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RisPortalEmployeeService {

	@Value("${app.portal.datasource.url:jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?currentSchema=hrms&sslmode=require}")
	private String portalDbUrl;

	@Value("${app.portal.datasource.username:postgres.zwjzyvsfmrpfkqosgvus}")
	private String portalDbUser;

	@Value("${app.portal.datasource.password:AYH8ciVAmIHOEP05}")
	private String portalDbPassword;

	public record PortalEmployee(
			Long employeeId,
			String employeeCode,
			String firstName,
			String lastName,
			String email,
			Long companyId
	) {
		public String getFullName() {
			String fn = firstName != null ? firstName.trim() : "";
			String ln = lastName != null ? lastName.trim() : "";
			return (fn + " " + ln).trim();
		}
	}

	public record PortalUserAuth(
			Long userId,
			Long employeeId,
			String employeeCode,
			String username,
			String email,
			String passwordHash,
			Boolean isActive,
			Boolean isLocked,
			Long companyId,
			String firstName,
			String lastName,
			List<String> roles,
			Set<String> rmsPermissions,
			boolean isSuperAdmin
	) {
		public String getFullName() {
			String fn = firstName != null ? firstName.trim() : "";
			String ln = lastName != null ? lastName.trim() : "";
			String full = (fn + " " + ln).trim();
			return full.isEmpty() ? (username != null ? username : email) : full;
		}

		public boolean hasRmsAccess() {
			return isSuperAdmin || (rmsPermissions != null && rmsPermissions.contains("RMS_ACCESS"));
		}
	}

	/**
	 * Find an employee in RIS Portal (HRMS) by their official or personal email.
	 */
	public Optional<PortalEmployee> findEmployeeByEmail(String email) {
		if (email == null || email.isBlank()) {
			return Optional.empty();
		}
		String cleanEmail = email.trim().toLowerCase();
		String sql = """
				SELECT id, employee_code, first_name, last_name, official_email, company_id
				FROM hrms.employee
				WHERE is_deleted = false
				  AND (lower(official_email) = ? OR lower(personal_email) = ?)
				LIMIT 1
				""";

		try (Connection conn = getConnection();
				PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setString(1, cleanEmail);
			stmt.setString(2, cleanEmail);

			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					PortalEmployee emp = new PortalEmployee(
							rs.getLong("id"),
							rs.getString("employee_code"),
							rs.getString("first_name"),
							rs.getString("last_name"),
							rs.getString("official_email"),
							rs.getLong("company_id")
					);
					return Optional.of(emp);
				}
			}
		} catch (Exception e) {
			log.warn("Could not query RIS Portal employee by email {}: {}", email, e.getMessage());
		}
		return Optional.empty();
	}

	/**
	 * Find a user in RIS Portal by username or email, including roles and RMS permissions.
	 */
	public Optional<PortalUserAuth> findUserAuth(String identifier) {
		if (identifier == null || identifier.isBlank()) {
			return Optional.empty();
		}
		String clean = identifier.trim().toLowerCase();
		String userSql = """
				SELECT u.id, u.employee_id, u.username, u.email, u.password_hash, u.is_active, u.is_locked,
				       e.employee_code, e.company_id, e.first_name, e.last_name
				FROM hrms.user_account u
				LEFT JOIN hrms.employee e ON e.id = u.employee_id
				WHERE u.is_deleted = false AND (lower(u.email) = ? OR lower(u.username) = ?)
				LIMIT 1
				""";

		try (Connection conn = getConnection();
				PreparedStatement userStmt = conn.prepareStatement(userSql)) {

			userStmt.setString(1, clean);
			userStmt.setString(2, clean);

			try (ResultSet rs = userStmt.executeQuery()) {
				if (!rs.next()) {
					return Optional.empty();
				}

				Long userId = rs.getLong("id");
				Long employeeId = rs.getObject("employee_id") != null ? rs.getLong("employee_id") : null;
				String username = rs.getString("username");
				String email = rs.getString("email");
				String passwordHash = rs.getString("password_hash");
				Boolean isActive = rs.getBoolean("is_active");
				Boolean isLocked = rs.getBoolean("is_locked");
				String employeeCode = rs.getString("employee_code");
				Long companyId = rs.getObject("company_id") != null ? rs.getLong("company_id") : null;
				String firstName = rs.getString("first_name");
				String lastName = rs.getString("last_name");

				// Query assigned roles
				List<String> roles = new ArrayList<>();
				String rolesSql = """
						SELECT DISTINCT r.role_code FROM hrms.user_role ur
						JOIN hrms.role r ON r.id = ur.role_id AND r.is_active = true AND r.is_deleted = false
						WHERE ur.user_id = ? AND ur.is_active = true
						""";
				try (PreparedStatement roleStmt = conn.prepareStatement(rolesSql)) {
					roleStmt.setLong(1, userId);
					try (ResultSet roleRs = roleStmt.executeQuery()) {
						while (roleRs.next()) {
							roles.add(roleRs.getString("role_code"));
						}
					}
				}

				boolean isSuperAdmin = roles.stream().anyMatch(r ->
						"SUPER_ADMIN".equalsIgnoreCase(r) ||
						"ROLE_SUPER_ADMIN".equalsIgnoreCase(r) ||
						"IT_ADMIN".equalsIgnoreCase(r)
				);

				// Query RMS permissions
				Set<String> perms = new HashSet<>();
				String permSql = """
						SELECT DISTINCT p.permission_code
						FROM hrms.user_role ur
						JOIN hrms.role r ON r.id = ur.role_id AND r.is_active = true AND r.is_deleted = false
						JOIN hrms.role_permission rp ON rp.role_id = r.id AND rp.is_active = true
						JOIN hrms.permission p ON p.id = rp.permission_id AND p.is_active = true AND p.is_deleted = false
						WHERE ur.user_id = ? AND ur.is_active = true AND p.permission_code LIKE 'RMS_%'
						UNION
						SELECT DISTINCT p.permission_code
						FROM hrms.user_permission up
						JOIN hrms.permission p ON p.id = up.permission_id AND p.is_active = true AND p.is_deleted = false
						WHERE up.user_id = ? AND up.is_active = true AND up.override_type = 'GRANT' AND p.permission_code LIKE 'RMS_%'
						""";
				try (PreparedStatement permStmt = conn.prepareStatement(permSql)) {
					permStmt.setLong(1, userId);
					permStmt.setLong(2, userId);
					try (ResultSet permRs = permStmt.executeQuery()) {
						while (permRs.next()) {
							perms.add(permRs.getString("permission_code"));
						}
					}
				}

				if (isSuperAdmin) {
					String allRmsPermsSql = "SELECT permission_code FROM hrms.permission WHERE permission_code LIKE 'RMS_%' AND is_active = true AND is_deleted = false";
					try (PreparedStatement allPermsStmt = conn.prepareStatement(allRmsPermsSql);
							ResultSet allRs = allPermsStmt.executeQuery()) {
						while (allRs.next()) {
							perms.add(allRs.getString("permission_code"));
						}
					}
				}

				return Optional.of(new PortalUserAuth(
						userId, employeeId, employeeCode, username, email, passwordHash,
						isActive, isLocked, companyId, firstName, lastName, roles, perms, isSuperAdmin
				));
			}
		} catch (Exception e) {
			log.error("Error querying RIS Portal user auth for {}: {}", identifier, e.getMessage(), e);
		}
		return Optional.empty();
	}

	/**
	 * Authenticate user with RIS Portal credentials.
	 */
	public Optional<PortalUserAuth> authenticate(String identifier, String rawPassword, PasswordHashUtil passwordHashUtil) {
		Optional<PortalUserAuth> opt = findUserAuth(identifier);
		if (opt.isEmpty()) {
			return Optional.empty();
		}
		PortalUserAuth user = opt.get();
		if (Boolean.FALSE.equals(user.isActive()) || Boolean.TRUE.equals(user.isLocked())) {
			throw new IllegalArgumentException("Your RIS Portal account is inactive or locked.");
		}
		if (passwordHashUtil == null || !passwordHashUtil.verifyPassword(rawPassword, user.passwordHash())) {
			return Optional.empty();
		}
		return Optional.of(user);
	}

	/**
	 * Direct portal access verification without HTTP integration key calls.
	 */
	public Optional<PortalAccessClient.Access> getPortalAccess(String email, Long rmsCompanyId) {
		Optional<PortalUserAuth> userOpt = findUserAuth(email);
		if (userOpt.isEmpty()) {
			return Optional.empty();
		}
		PortalUserAuth user = userOpt.get();
		if (!user.hasRmsAccess()) {
			return Optional.empty();
		}

		Set<Long> companies = new HashSet<>();
		if (user.companyId() != null) {
			companies.add(user.companyId());
		}
		companies.add(1L); // Default RIS company
		if (rmsCompanyId != null) {
			companies.add(rmsCompanyId);
		}

		long portalUserId = user.userId();
		long portalEmpId = user.employeeId() != null ? user.employeeId() : portalUserId;

		return Optional.of(new PortalAccessClient.Access(
				portalUserId,
				portalEmpId,
				Set.copyOf(user.rmsPermissions()),
				Set.copyOf(companies)
		));
	}

	/**
	 * Retrieve internal employees eligible for resourcing.
	 */
	public Set<Long> getEligibleInternalEmployees(long companyId) {
		Set<Long> ids = new HashSet<>();
		String sql = "SELECT id FROM hrms.employee WHERE is_deleted = false AND company_id = ?";
		try (Connection conn = getConnection();
				PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, companyId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getLong("id"));
				}
			}
		} catch (Exception e) {
			log.warn("Could not query eligible employees for company {}: {}", companyId, e.getMessage());
		}
		return Set.copyOf(ids);
	}

	private Connection getConnection() throws Exception {
		return DriverManager.getConnection(portalDbUrl, portalDbUser, portalDbPassword);
	}
}

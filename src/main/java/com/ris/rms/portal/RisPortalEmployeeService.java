package com.ris.rms.portal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

		try (Connection conn = DriverManager.getConnection(portalDbUrl, portalDbUser, portalDbPassword);
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
}


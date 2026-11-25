package com.ris.rms.portal;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * One-way synchronization from RIS Portal (hrms) to RMS (rms.employee & rms.department).
 * Syncs only active development/delivery employees (BILLABLE, BENCH, INTERNAL_PROJECT)
 * and explicitly excludes Management, IT Admins, Administration, HR, and Marketing.
 * Never writes back to RIS Portal (hrms): records added manually in RMS stay strictly in RMS.
 */
@Slf4j
@Service
public class RisPortalEmployeeSyncService {

	private static final long SYNC_COOLDOWN_MS = 10_000L;
	private static final long RMS_COMPANY_ID = 1L;

	private static final Set<String> DEV_RESOURCE_STATUSES = Set.of("BILLABLE", "BENCH", "INTERNAL_PROJECT");
	private static final Set<String> EXCLUDED_DEPARTMENTS = Set.of(
			"MANAGEMENT",
			"EXECUTIVE MANAGEMENT",
			"IT TEAM",
			"ADMIN & FACILITIES",
			"HUMAN RESOURCES",
			"FINANCE & ACCOUNTS",
			"MARKETING",
			"SALES & MARKETING",
			"DIGITAL MARKETING",
			"TALENT ACQUISITION",
			"PROJECT MANAGEMENT OFFICE"
	);

	private final JdbcTemplate jdbc;
	private TransactionTemplate syncTxTemplate;
	private final AtomicLong lastSyncMs = new AtomicLong(0L);
	private final Object syncLock = new Object();
	private volatile Set<String> excludedEmails = Set.of();

	@Value("${app.portal.datasource.url:jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?currentSchema=hrms&sslmode=require}")
	private String portalDbUrl;

	@Value("${app.portal.datasource.username:postgres.zwjzyvsfmrpfkqosgvus}")
	private String portalDbUser;

	@Value("${app.portal.datasource.password:AYH8ciVAmIHOEP05}")
	private String portalDbPassword;

	public RisPortalEmployeeSyncService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Autowired(required = false)
	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		if (transactionManager != null) {
			TransactionTemplate tpl = new TransactionTemplate(transactionManager);
			tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			tpl.setReadOnly(false);
			this.syncTxTemplate = tpl;
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		try {
			syncNow();
		} catch (Exception e) {
			log.warn("Initial RIS Portal development employee sync skipped: {}", e.getMessage());
		}
	}

	public void syncIfNeeded() {
		long now = System.currentTimeMillis();
		if (now - lastSyncMs.get() < SYNC_COOLDOWN_MS) {
			return;
		}
		synchronized (syncLock) {
			if (System.currentTimeMillis() - lastSyncMs.get() < SYNC_COOLDOWN_MS) {
				return;
			}
			syncNow();
		}
	}

	public Set<Long> getEligibleEmployeeIds(long companyId) {
		try {
			syncIfNeeded();
		} catch (Exception e) {
			log.warn("RIS Portal sync error during eligible check: {}", e.getMessage());
		}
		Set<String> excluded = this.excludedEmails;
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT employee_id, email FROM rms.employee WHERE company_id = ?", companyId);
		Set<Long> eligible = new HashSet<>();
		for (Map<String, Object> row : rows) {
			Long id = ((Number) row.get("employee_id")).longValue();
			String email = row.get("email") != null ? row.get("email").toString().trim().toLowerCase() : null;
			if (email != null && excluded.contains(email)) {
				continue;
			}
			eligible.add(id);
		}
		return Set.copyOf(eligible);
	}

	public void syncNow() {
		synchronized (syncLock) {
			String sql = """
					SELECT e.id, e.first_name, e.last_name, e.official_email, e.personal_email,
					       e.phone, e.gender, e.date_of_joining, e.employment_type,
					       e.resource_status, e.is_active, e.is_deleted,
					       e.highest_qualification, e.primary_skills, e.secondary_skills,
					       e.city, e.state, e.nationality, e.pin_code,
					       e.previous_employer, d.department_name, des.designation_name
					FROM hrms.employee e
					LEFT JOIN hrms.department d ON d.id = e.department_id
					LEFT JOIN hrms.designation des ON des.id = e.designation_id
					ORDER BY e.id
					""";

			List<DevEmployeeRow> devRows = new ArrayList<>();
			Set<String> nonDevOrInactiveEmails = new HashSet<>();

			try (Connection conn = DriverManager.getConnection(portalDbUrl, portalDbUser, portalDbPassword);
					PreparedStatement stmt = conn.prepareStatement(sql);
					ResultSet rs = stmt.executeQuery()) {

				while (rs.next()) {
					long hrmsId = rs.getLong("id");
					boolean isActive = rs.getBoolean("is_active");
					boolean isDeleted = rs.getBoolean("is_deleted");
					String resStatus = trimUpper(rs.getString("resource_status"));
					String deptName = trimToNull(rs.getString("department_name"));
					String deptUpper = deptName != null ? deptName.toUpperCase(Locale.ROOT) : "";

					String officialEmail = trimToNull(rs.getString("official_email"));
					String personalEmail = trimToNull(rs.getString("personal_email"));

					boolean isDev = !isDeleted
							&& isActive
							&& resStatus != null
							&& DEV_RESOURCE_STATUSES.contains(resStatus)
							&& !EXCLUDED_DEPARTMENTS.contains(deptUpper);

					if (!isDev) {
						if (officialEmail != null) nonDevOrInactiveEmails.add(officialEmail.toLowerCase(Locale.ROOT));
						if (personalEmail != null) nonDevOrInactiveEmails.add(personalEmail.toLowerCase(Locale.ROOT));
						continue;
					}

					String email = officialEmail != null ? officialEmail
							: (personalEmail != null ? personalEmail : "ris-emp-" + hrmsId + "@rudhrainfosolutions.com");
					String mappedPersonal = (personalEmail != null && !personalEmail.equalsIgnoreCase(email)) ? personalEmail : null;

					devRows.add(new DevEmployeeRow(
							hrmsId,
							truncate(nonBlank(rs.getString("first_name"), "Employee"), 100),
							truncate(rs.getString("last_name") != null ? rs.getString("last_name").trim() : "", 100),
							truncate(email.toLowerCase(Locale.ROOT), 255),
							truncate(mappedPersonal != null ? mappedPersonal.toLowerCase(Locale.ROOT) : null, 255),
							truncate(trimToNull(rs.getString("phone")), 50),
							truncate(trimToNull(rs.getString("gender")), 20),
							rs.getDate("date_of_joining"),
							mapEmploymentType(rs.getString("employment_type")),
							mapStatus(resStatus),
							truncate(trimToNull(rs.getString("highest_qualification")), 100),
							trimToNull(rs.getString("primary_skills")),
							trimToNull(rs.getString("secondary_skills")),
							truncate(trimToNull(rs.getString("city")), 100),
							truncate(trimToNull(rs.getString("state")), 100),
							truncate(trimToNull(rs.getString("nationality")), 100),
							truncate(trimToNull(rs.getString("pin_code")), 20),
							truncate(trimToNull(rs.getString("previous_employer")), 255),
							deptName,
							truncate(trimToNull(rs.getString("designation_name")), 255)
					));
				}
			} catch (Exception e) {
				log.warn("Failed reading RIS Portal employees for one-way sync: {}", e.getMessage());
				return;
			}

			if (syncTxTemplate != null) {
				syncTxTemplate.executeWithoutResult(status -> applyLocalSync(devRows, nonDevOrInactiveEmails));
			} else {
				applyLocalSync(devRows, nonDevOrInactiveEmails);
			}
		}
	}

	private void applyLocalSync(List<DevEmployeeRow> devRows, Set<String> nonDevOrInactiveEmails) {
		// Ensure department sequence is aligned before inserting missing departments
		resetDepartmentSequence();
		Map<String, Long> deptCache = loadDepartmentCache();

		int inserted = 0;
		int updated = 0;
		for (DevEmployeeRow emp : devRows) {
			Long deptId = resolveOrCreateDepartment(emp.departmentName(), deptCache);
			Long existingIdByEmail = findEmployeeIdByEmail(emp.email());

			if (existingIdByEmail != null) {
				updateExistingEmployee(existingIdByEmail, emp, deptId);
				updated++;
			} else if (!employeeIdExists(emp.hrmsId())) {
				insertEmployeeWithId(emp.hrmsId(), emp, deptId);
				inserted++;
			} else {
				// If hrms.id collides with an unrelated local RMS row (e.g., dummy row 7), allocate a fresh sequence ID
				resetEmployeeSequence();
				Long nextId = jdbc.queryForObject("SELECT nextval('rms.employee_employee_id_seq')", Long.class);
				insertEmployeeWithId(nextId, emp, deptId);
				inserted++;
			}
		}

		resetEmployeeSequence();
		this.excludedEmails = Set.copyOf(nonDevOrInactiveEmails);
		lastSyncMs.set(System.currentTimeMillis());

		if (inserted > 0) {
			log.info("RIS Portal -> RMS one-way sync complete: {} inserted, {} updated", inserted, updated);
		} else {
			log.debug("RIS Portal -> RMS one-way sync complete: {} updated", updated);
		}
	}

	private Map<String, Long> loadDepartmentCache() {
		Map<String, Long> cache = new HashMap<>();
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT department_id, department_name FROM rms.department WHERE company_id = ?", RMS_COMPANY_ID);
		for (Map<String, Object> row : rows) {
			String name = row.get("department_name") != null ? row.get("department_name").toString().trim() : null;
			if (name != null && !name.isEmpty()) {
				cache.put(name.toLowerCase(Locale.ROOT), ((Number) row.get("department_id")).longValue());
			}
		}
		return cache;
	}

	private Long resolveOrCreateDepartment(String deptName, Map<String, Long> cache) {
		if (deptName == null || deptName.isBlank()) {
			return null;
		}
		String key = deptName.toLowerCase(Locale.ROOT);
		Long existing = cache.get(key);
		if (existing != null) {
			return existing;
		}
		Long newId = jdbc.queryForObject(
				"INSERT INTO rms.department (company_id, department_name) VALUES (?, ?) RETURNING department_id",
				Long.class, RMS_COMPANY_ID, truncate(deptName, 150));
		if (newId != null) {
			cache.put(key, newId);
		}
		return newId;
	}

	private Long findEmployeeIdByEmail(String email) {
		List<Long> ids = jdbc.queryForList(
				"SELECT employee_id FROM rms.employee WHERE company_id = ? AND lower(email) = lower(?) LIMIT 1",
				Long.class, RMS_COMPANY_ID, email);
		return ids.isEmpty() ? null : ids.getFirst();
	}

	private boolean employeeIdExists(long employeeId) {
		Integer cnt = jdbc.queryForObject("SELECT count(*) FROM rms.employee WHERE employee_id = ?", Integer.class, employeeId);
		return cnt != null && cnt > 0;
	}

	private void insertEmployeeWithId(long employeeId, DevEmployeeRow emp, Long deptId) {
		String safePersonal = isPersonalEmailAvailable(emp.personalEmail(), null) ? emp.personalEmail() : null;
		jdbc.update("""
				INSERT INTO rms.employee (
				    employee_id, company_id, first_name, last_name, email, personalemailid,
				    phone_number, primary_contact_no, gender, joining_date, employment_type, status,
				    department_id, job_title, current_job_title, highest_qualification,
				    primary_skills, secondary_skills, location, city, state, country, zip_code,
				    most_recent_employer, createddt, updateddt
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
				""",
				employeeId, RMS_COMPANY_ID, emp.firstName(), emp.lastName(), emp.email(), safePersonal,
				emp.phone(), truncate(emp.phone(), 30), emp.gender(), emp.joiningDate(), emp.employmentType(), emp.status(),
				deptId, emp.jobTitle(), emp.jobTitle(), emp.highestQualification(),
				emp.primarySkills(), emp.secondarySkills(), emp.city(), emp.city(), emp.state(), emp.country(), emp.zipCode(),
				emp.previousEmployer()
		);
	}

	private void updateExistingEmployee(long employeeId, DevEmployeeRow emp, Long deptId) {
		String safePersonal = isPersonalEmailAvailable(emp.personalEmail(), employeeId) ? emp.personalEmail() : null;
		jdbc.update("""
				UPDATE rms.employee SET
				    first_name = ?,
				    last_name = ?,
				    phone_number = COALESCE(?, phone_number),
				    primary_contact_no = COALESCE(?, primary_contact_no),
				    gender = COALESCE(?, gender),
				    joining_date = COALESCE(?, joining_date),
				    employment_type = COALESCE(?, employment_type),
				    status = CASE WHEN status IN ('Client', 'Inactive') THEN status ELSE ? END,
				    department_id = COALESCE(?, department_id),
				    job_title = COALESCE(?, job_title),
				    current_job_title = COALESCE(?, current_job_title),
				    highest_qualification = COALESCE(?, highest_qualification),
				    primary_skills = COALESCE(?, primary_skills),
				    secondary_skills = COALESCE(?, secondary_skills),
				    personalemailid = COALESCE(?, personalemailid),
				    location = COALESCE(?, location),
				    city = COALESCE(?, city),
				    state = COALESCE(?, state),
				    country = COALESCE(?, country),
				    zip_code = COALESCE(?, zip_code),
				    most_recent_employer = COALESCE(?, most_recent_employer),
				    updateddt = NOW()
				WHERE employee_id = ?
				""",
				emp.firstName(), emp.lastName(),
				emp.phone(), truncate(emp.phone(), 30), emp.gender(), emp.joiningDate(), emp.employmentType(),
				emp.status(), deptId, emp.jobTitle(), emp.jobTitle(), emp.highestQualification(),
				emp.primarySkills(), emp.secondarySkills(), safePersonal,
				emp.city(), emp.city(), emp.state(), emp.country(), emp.zipCode(), emp.previousEmployer(),
				employeeId
		);
	}

	private boolean isPersonalEmailAvailable(String personalEmail, Long selfEmployeeId) {
		if (personalEmail == null || personalEmail.isBlank()) {
			return false;
		}
		Integer cnt = selfEmployeeId == null
				? jdbc.queryForObject(
						"SELECT count(*) FROM rms.employee WHERE company_id = ? AND lower(personalemailid) = lower(?)",
						Integer.class, RMS_COMPANY_ID, personalEmail)
				: jdbc.queryForObject(
						"SELECT count(*) FROM rms.employee WHERE company_id = ? AND lower(personalemailid) = lower(?) AND employee_id <> ?",
						Integer.class, RMS_COMPANY_ID, personalEmail, selfEmployeeId);
		return cnt == null || cnt == 0;
	}

	private void resetEmployeeSequence() {
		jdbc.queryForObject(
				"SELECT setval(pg_get_serial_sequence('rms.employee', 'employee_id'), COALESCE((SELECT MAX(employee_id) FROM rms.employee), 1))",
				Long.class);
	}

	private void resetDepartmentSequence() {
		jdbc.queryForObject(
				"SELECT setval(pg_get_serial_sequence('rms.department', 'department_id'), COALESCE((SELECT MAX(department_id) FROM rms.department), 1))",
				Long.class);
	}

	private static String mapStatus(String resourceStatus) {
		if ("BILLABLE".equalsIgnoreCase(resourceStatus)) return "Billable";
		if ("INTERNAL_PROJECT".equalsIgnoreCase(resourceStatus)) return "Internal";
		return "Bench";
	}

	private static String mapEmploymentType(String empType) {
		if (empType == null || empType.isBlank()) return "Regular";
		String upper = empType.trim().toUpperCase(Locale.ROOT);
		if (upper.contains("CONTRACT")) return "Contract";
		if (upper.contains("INTERN")) return "Intern";
		if (upper.contains("PART")) return "Part-Time";
		return "Regular";
	}

	private static String trimToNull(String val) {
		if (val == null) return null;
		String t = val.trim();
		return t.isEmpty() ? null : t;
	}

	private static String trimUpper(String val) {
		String t = trimToNull(val);
		return t != null ? t.toUpperCase(Locale.ROOT) : null;
	}

	private static String nonBlank(String val, String fallback) {
		String t = trimToNull(val);
		return t != null ? t : fallback;
	}

	private static String truncate(String val, int maxLen) {
		if (val == null) return null;
		return val.length() <= maxLen ? val : val.substring(0, maxLen);
	}

	private record DevEmployeeRow(
			long hrmsId,
			String firstName,
			String lastName,
			String email,
			String personalEmail,
			String phone,
			String gender,
			java.sql.Date joiningDate,
			String employmentType,
			String status,
			String highestQualification,
			String primarySkills,
			String secondarySkills,
			String city,
			String state,
			String country,
			String zipCode,
			String previousEmployer,
			String departmentName,
			String jobTitle
	) {}
}

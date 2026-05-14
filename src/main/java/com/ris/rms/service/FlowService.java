package com.ris.rms.service;

import java.time.LocalDate;
import java.util.Map;

public interface FlowService {
	Map<String, Object> getEmployeeFlow(Long employeeId, int page, int size, LocalDate fromDate, LocalDate toDate);

	Map<String, Object> listEmployeeFlows(int page, int size, Long companyId, String q, String status,
			Long departmentId, LocalDate fromDate, LocalDate toDate);

	Map<String, Object> getCandidateFlow(Long candidateId, int page, int size, LocalDate fromDate, LocalDate toDate);

	Map<String, Object> listCandidateFlows(int page, int size, Long companyId, String q, String status,
			String sourceType, LocalDate fromDate, LocalDate toDate);
}

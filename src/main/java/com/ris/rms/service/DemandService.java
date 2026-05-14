package com.ris.rms.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ris.rms.dto.DemandCreateDto;
import com.ris.rms.dto.DemandReportRequest;
import com.ris.rms.dto.DemandResponseDto;
import com.ris.rms.dto.GroupFlowDto;

public interface DemandService {

	DemandResponseDto createDemand(DemandCreateDto dto);

	DemandResponseDto updateDemand(Long demandId, DemandCreateDto dto);

	DemandResponseDto getDemandById(Long demandId);

	List<DemandResponseDto> listDemands(Long companyId, Long accountId, Long departmentId, String status, Integer page,
			Integer size);

	Page<GroupFlowDto> getDemandFlowList(Long companyId, Long accountId, Long departmentId, String status,
			String fromDate, String toDate, Pageable pageable);

	void generateReport(DemandReportRequest request);

	byte[] generateExcelReport(DemandReportRequest request) throws Exception;
	
	void updateDemandStatusOnResumeShare(Long demandId);
}
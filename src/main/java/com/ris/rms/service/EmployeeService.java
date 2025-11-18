package com.ris.rms.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.dto.EmployeeDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ResumeShareDto;

public interface EmployeeService {

	EmployeeDto getById(Long id);

	List<EmployeeDto> list(Long companyId, String q, String status, Long departmentId, Integer page, Integer size);

	EmployeeDto update(Long id, EmployeeDto dto, MultipartFile resume) throws IOException, Exception;
	
	void delete(Long id);
	
	EmployeeDto create(EmployeeDto dto, org.springframework.web.multipart.MultipartFile resume) throws IOException, Exception;

	ResumeStorageService.ResumeResource getResumeByEmployeeId(Long employeeId) throws Exception;
	
	ImportResultDto importEmployees(Long companyId, InputStream inputStream, String filename) throws Exception;
	
	ResumeShareDto shareResume(ResumeShareDto shareRequest) throws Exception;
	
}

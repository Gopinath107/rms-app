package com.ris.rms.service;

import java.io.InputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ris.rms.dto.CandidateDto;
import com.ris.rms.dto.ImportResultDto;
import com.ris.rms.dto.ResumeShareDto;
import com.ris.rms.service.ResumeStorageService.ResumeResource;

public interface CandidateService {

	CandidateDto create(CandidateDto dto, MultipartFile resume) throws Exception;

	CandidateDto update(Long id, CandidateDto dto, MultipartFile resume) throws Exception;

	CandidateDto getById(Long id);

	List<CandidateDto> list(Long companyId, String q, String status, String sourceType, Integer page, Integer size);

	void delete(Long id);
	
	ResumeShareDto shareResume(ResumeShareDto request) throws Exception;

    ResumeResource getResumeByCandidateId(Long candidateId) throws Exception;

    ImportResultDto importCandidates(Long companyId, InputStream inputStream, String filename) throws Exception;

}

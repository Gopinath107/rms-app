package com.ris.rms.service;

import com.ris.rms.dto.AllocationDto;

import java.util.List;

public interface AllocationService {
    AllocationDto create(AllocationDto dto);
    AllocationDto getById(Long id);
    AllocationDto update(Long allocationId, AllocationDto dto);
    void delete(Long allocationId);

    List<AllocationDto> list(Long companyId, Long projectId, Long employeeId, Long candidateId,
            String status, Boolean billable,
            Integer page, Integer size);
}
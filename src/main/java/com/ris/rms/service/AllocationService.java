package com.ris.rms.service;

import com.ris.rms.dto.AllocationDto;

import java.util.List;

public interface AllocationService {
    AllocationDto create(AllocationDto dto);
    AllocationDto getById(Long id);
    List<AllocationDto> list(Long companyId, Long projectId, Long employeeId,
                             String status, Boolean billable,
                             Integer page, Integer size);
    AllocationDto update(Long allocationId, AllocationDto dto);
    void delete(Long allocationId);
}

package com.ris.rms.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class BulkCreateResponseDto {
    private ResourceRequestGroupDto group;
    private List<ResourceRequestDto> createdRequests; 
}

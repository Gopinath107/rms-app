package com.ris.rms.dto;

import java.util.List;
import lombok.Data;

@Data
public class DetailedResourceReportRequest {
    private Long userId;
    private String fromDate;
    private String toDate;
    private Long accountId;
    private List<Long> demandIds;
}

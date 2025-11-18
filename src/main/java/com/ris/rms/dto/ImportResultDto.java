package com.ris.rms.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportResultDto {
    private int totalRows;
    private int successCount;
    private int failureCount;
    private List<String> errors = new ArrayList<>();
}
package com.ris.rms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlowRequest {

   @NotNull
   private Long employeeId;
   
   @Min(0)
   private Integer page = 0;
   
   @Min(1)
   private Integer size = 10;
   
   private String fromDate;
   
   private String toDate;
}

package com.ris.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SkillDto {
    private Long skillId;

    @NotBlank(message = "skillName is required")
    @Size(max = 100, message = "skillName must be <= 100 chars")
    private String skillName;
}

package com.ris.rms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleDto {
    private Long roleId;

    @NotNull(message = "companyId is required")
    private Long companyId;

    private String companyName;

    @NotBlank(message = "roleName is required")
    @Size(max = 100, message = "roleName must be <= 100 chars")
    private String roleName;
}

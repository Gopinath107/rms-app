package com.ris.rms.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AuthUserDto {
    private Long userId;
    private Long companyId;
    private String companyName;

    private Long employeeId;
    private String employeeName;

    private Long roleId;
    private String roleName;

    private String email;
    private Boolean isActive;
}

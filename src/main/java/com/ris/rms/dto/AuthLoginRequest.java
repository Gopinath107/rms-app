package com.ris.rms.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AuthLoginRequest {
    private String email;
    private String password;
}

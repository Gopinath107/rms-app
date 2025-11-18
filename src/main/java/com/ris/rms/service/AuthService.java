package com.ris.rms.service;

import com.ris.rms.dto.AuthUserDto;

public interface AuthService {
    AuthUserDto login(String email, String password);
}

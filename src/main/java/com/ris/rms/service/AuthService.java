package com.ris.rms.service;

import com.ris.rms.dto.AuthUserDto;

public interface AuthService {
//    AuthUserDto login(String email, String password);
	AuthUserDto login(String email, String password, Long roleId);
	void generateAndSendOtp(String email);
	void verifyOtp(String email, String otp);
	void resetPassword(String email, String otp, String newPassword);
}

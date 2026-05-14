package com.ris.rms.controller;

import com.ris.rms.dto.AuthLoginRequest;
import com.ris.rms.dto.AuthUserDto;
import com.ris.rms.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthLoginRequest body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            AuthUserDto user = authService.login(body.getEmail(), body.getPassword(), body.getRoleId());
            
            resp.put("result", user);
            resp.put("success", true);
            resp.put("errors", List.of());
            resp.put("errorCount", 0);
            return ResponseEntity.ok(resp); 
        } catch (Exception e) {
            resp.put("result", null);
            resp.put("success", false);
            resp.put("errors", List.of(e.getMessage()));
            resp.put("errorCount", 1);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            authService.generateAndSendOtp(body.get("email"));
            resp.put("success", true);
            resp.put("errors", List.of());
            resp.put("message", "OTP sent successfully.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("errors", List.of(e.getMessage()));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("errors", List.of("An unexpected error occurred while sending the OTP. Please try again later."));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            authService.verifyOtp(body.get("email"), body.get("otp"));
            resp.put("success", true);
            resp.put("errors", List.of());
            resp.put("message", "OTP verified.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("errors", List.of(e.getMessage()));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("errors", List.of("An unexpected error occurred while verifying the OTP. Please try again later."));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> body) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            authService.resetPassword(body.get("email"), body.get("otp"), body.get("newPassword"));
            resp.put("success", true);
            resp.put("errors", List.of());
            resp.put("message", "Password reset successfully.");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("errors", List.of(e.getMessage()));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("errors", List.of("An unexpected error occurred while resetting the password. Please try again later."));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }
}
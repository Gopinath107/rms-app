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
            AuthUserDto user = authService.login(body.getEmail(), body.getPassword());
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
}

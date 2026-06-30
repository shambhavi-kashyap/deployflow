package com.deployflow.auth.controller;

import com.deployflow.auth.service.AuthService;
import com.deployflow.user.dto.RegisterRequest;
import com.deployflow.user.dto.RegisterResponse;
import com.deployflow.user.dto.LoginRequest;
import com.deployflow.user.dto.AuthResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

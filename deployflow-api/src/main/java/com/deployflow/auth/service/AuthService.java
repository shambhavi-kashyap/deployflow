package com.deployflow.auth.service;

import com.deployflow.user.dto.RegisterRequest;
import com.deployflow.user.dto.RegisterResponse;
import com.deployflow.user.dto.LoginRequest;
import com.deployflow.user.dto.AuthResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
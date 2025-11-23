package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.LoginRequest;
import com.company.company_clean_hub_be.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

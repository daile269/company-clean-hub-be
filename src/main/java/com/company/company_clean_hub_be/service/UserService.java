package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.UserRequest;
import com.company.company_clean_hub_be.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse createUser(UserRequest request);
    UserResponse updateUser(Long id, UserRequest request);
    void deleteUser(Long id);
}

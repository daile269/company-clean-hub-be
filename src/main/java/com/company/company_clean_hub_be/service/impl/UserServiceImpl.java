package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.UserRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.UserResponse;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<UserResponse> getUsersWithFilter(String keyword, Long roleId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findByFilters(keyword, roleId, pageable);

        List<UserResponse> users = userPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<UserResponse>builder()
                .content(users)
                .page(userPage.getNumber())
                .pageSize(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        return mapToResponse(user);
    }

    @Override
    public UserResponse createUser(UserRequest request) {
        // Kiểm tra trùng username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng phone
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(role)
                .status(request.getStatus())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        // Kiểm tra trùng username (ngoại trừ chính nó)
        if (userRepository.existsByUsernameAndIdNot(request.getUsername(), id)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        
        // Kiểm tra trùng phone (ngoại trừ chính nó)
        if (request.getPhone() != null && userRepository.existsByPhoneAndIdNot(request.getPhone(), id)) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        user.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRole(role);
        user.setStatus(request.getStatus());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_IS_NOT_EXISTS);
        }
        userRepository.deleteById(id);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

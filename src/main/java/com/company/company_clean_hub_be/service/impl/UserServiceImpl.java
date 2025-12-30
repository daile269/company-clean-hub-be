package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.UserRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.UserPermissionsResponse;
import com.company.company_clean_hub_be.dto.response.UserResponse;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.company.company_clean_hub_be.dto.request.PasswordChangeRequest;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponse> getAllUsers() {
    log.info("getAllUsers requested by {}", getCurrentUsername());
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<UserResponse> getUsersWithFilter(String keyword, Long roleId, int page, int pageSize) {
        log.info("getUsersWithFilter requested by {}: keyword='{}', roleId={}, page={}, pageSize={}", getCurrentUsername(), keyword, roleId, page, pageSize);
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
        log.info("getUserById requested by {}: id={}", getCurrentUsername(), id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        return mapToResponse(user);
    }

    @Override
    public UserResponse createUser(UserRequest request) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("createUser requested by {}: username='{}', phone={}", actor, request.getUsername(), request.getPhone());
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
        log.info("createUser completed by {}: id={}", actor, savedUser.getId());
        return mapToResponse(savedUser);
    }

    @Override
    public UserResponse updateUser(Long id, UserRequest request) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("updateUser requested by {}: id={}, username='{}'", actor, id, request.getUsername());
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
        log.info("updateUser completed by {}: id={}", actor, updatedUser.getId());
        return mapToResponse(updatedUser);
    }

    @Override
    public void deleteUser(Long id) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("deleteUser requested by {}: id={}", actor, id);
        if (!userRepository.existsById(id)) {
            throw new AppException(ErrorCode.USER_IS_NOT_EXISTS);
        }
        userRepository.deleteById(id);
        log.info("deleteUser completed by {}: id={}", actor, id);
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
    @Override
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            return (String) principal;
        }

        return null;
    }

    @Override
    public UserPermissionsResponse getCurrentUserPermissions() {
        String username = getCurrentUsername();
        log.info("getCurrentUserPermissions requested by: {}", username);
        
        if (username == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        Role role = user.getRole();
        
        return UserPermissionsResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .roleCode(role.getCode())
                .roleName(role.getName())
                .permissions(role.getPermissions())
                .build();
    }

    @Override
    public void changePasswordForCurrentUser(PasswordChangeRequest request) {
        String username = getCurrentUsername();
        log.info("changePasswordForCurrentUser requested by {}", username);
        if (username == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("changePasswordForCurrentUser completed for user={}", username);
    }

    @Override
    public void changePasswordForUser(Long id, PasswordChangeRequest request) {
        log.info("changePasswordForUser requested by {}: targetUserId={}", getCurrentUsername(), id);
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_NOT_MATCH);
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("changePasswordForUser completed: id={}", id);
    }
}

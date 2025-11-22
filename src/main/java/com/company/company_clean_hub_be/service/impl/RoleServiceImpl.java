package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.RoleRequest;
import com.company.company_clean_hub_be.dto.response.RoleResponse;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;

    @Override
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        return mapToResponse(role);
    }

    @Override
    public RoleResponse createRole(RoleRequest request) {
        Role role = Role.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Role savedRole = roleRepository.save(role);
        return mapToResponse(savedRole);
    }

    @Override
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        role.setName(request.getName());
        role.setCode(request.getCode());
        role.setDescription(request.getDescription());
        role.setUpdatedAt(LocalDateTime.now());

        Role updatedRole = roleRepository.save(role);
        return mapToResponse(updatedRole);
    }

    @Override
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        roleRepository.delete(role);
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}

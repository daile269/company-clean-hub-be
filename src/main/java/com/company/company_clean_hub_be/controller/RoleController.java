package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.RoleRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.RoleResponse;
import com.company.company_clean_hub_be.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/roles")
public class RoleController {
    private final RoleService roleService;

    @GetMapping
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ApiResponse.success("Lấy danh sách vai trò thành công", roles, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> getRoleById(@PathVariable Long id) {
        RoleResponse role = roleService.getRoleById(id);
        return ApiResponse.success("Lấy thông tin vai trò thành công", role, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        RoleResponse role = roleService.createRole(request);
        return ApiResponse.success("Tạo vai trò thành công", role, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        RoleResponse role = roleService.updateRole(id, request);
        return ApiResponse.success("Cập nhật vai trò thành công", role, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.success("Xóa vai trò thành công", null, HttpStatus.OK.value());
    }
}

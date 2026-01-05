package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.UserRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.UserPermissionsResponse;
import com.company.company_clean_hub_be.dto.response.UserResponse;
import com.company.company_clean_hub_be.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import com.company.company_clean_hub_be.dto.request.PasswordChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me/permissions")
    public ApiResponse<UserPermissionsResponse> getCurrentUserPermissions() {
        UserPermissionsResponse permissions = userService.getCurrentUserPermissions();
        return ApiResponse.success("Lấy quyền người dùng thành công", permissions, HttpStatus.OK.value());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ApiResponse.success("Lấy danh sách người dùng thành công", users, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    @PreAuthorize("hasAuthority('USER_VIEW') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<PageResponse<UserResponse>> getUsersWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<UserResponse> users = userService.getUsersWithFilter(keyword, roleId, page, pageSize);
        return ApiResponse.success("Lấy danh sách người dùng thành công", users, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ApiResponse.success("Lấy thông tin người dùng thành công", user, HttpStatus.OK.value());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse user = userService.createUser(request);
        return ApiResponse.success("Tạo người dùng thành công", user, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_EDIT') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request) {
        UserResponse user = userService.updateUser(id, request);
        return ApiResponse.success("Cập nhật người dùng thành công", user, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success("Xóa người dùng thành công", null, HttpStatus.OK.value());
    }

    @PostMapping("/me/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changeMyPassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePasswordForCurrentUser(request);
        return ApiResponse.success("Đổi mật khẩu thành công", null, HttpStatus.OK.value());
    }

    @PostMapping("/{id}/change-password")
    @PreAuthorize("hasAuthority('USER_EDIT') or hasAuthority('USER_MANAGE_ALL')")
    public ApiResponse<Void> changeUserPassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePasswordForUser(id, request);
        return ApiResponse.success("Đổi mật khẩu cho người dùng thành công", null, HttpStatus.OK.value());
    }
}

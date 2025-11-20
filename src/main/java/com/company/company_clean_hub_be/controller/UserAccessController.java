package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.common.ApiResponse;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.service.RoleService;
import com.company.company_clean_hub_be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Nhóm 1 – Phân quyền & Quản trị người dùng (User & Access Management)
 *
 * Services chính (thuộc tầng service, được sử dụng bởi controller này):
 * - AuthService (xác thực / đăng nhập)
 * - UserService (quản lý tài khoản người dùng)
 * - RoleService (quản lý vai trò / nhóm quyền)
 * - PermissionService (nếu tách chi tiết quyền) — optional
 * - UserProfileService (yêu cầu chỉnh sửa thông tin cá nhân)
 * - UserFeedbackService (tiếp nhận phản hồi / góp ý của nhân viên)
 *
 * Giải thích:
 * Nhóm này chịu trách nhiệm toàn bộ việc quản trị người dùng và phân quyền:
 * - Đăng nhập / xác thực
 * - Phân quyền các loại user (ví dụ: admin, manager, accountant, staff...)
 * - Quản lý tài khoản, duyệt yêu cầu chỉnh sửa thông tin, tiếp nhận phản hồi.
 *
 * Lý do gom vào một controller:
 * Đa số endpoint liên quan tới users, roles, quyền và feedback có cùng ngữ cảnh bảo mật
 * và quản trị — hợp lý khi tập trung để tiện triển khai middleware/auth checks.
 */
@RestController
@RequestMapping("/api/user-access")
@RequiredArgsConstructor
public class UserAccessController {

}

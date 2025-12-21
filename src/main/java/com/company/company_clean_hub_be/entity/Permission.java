package com.company.company_clean_hub_be.entity;

/**
 * Enum định nghĩa các quyền (permissions) trong hệ thống
 */
public enum Permission {
    // Employee permissions
    EMPLOYEE_VIEW("Xem thông tin nhân viên"),
    EMPLOYEE_VIEW_OWN("Xem thông tin cá nhân"),
    EMPLOYEE_CREATE("Tạo nhân viên mới"),
    EMPLOYEE_EDIT("Chỉnh sửa thông tin nhân viên"),
    EMPLOYEE_DELETE("Xóa nhân viên"),
    
    // Customer permissions
    CUSTOMER_VIEW("Xem thông tin khách hàng"),
    CUSTOMER_CREATE("Tạo khách hàng mới"),
    CUSTOMER_EDIT("Chỉnh sửa thông tin khách hàng"),
    CUSTOMER_DELETE("Xóa khách hàng"),
    CUSTOMER_ASSIGN("Phân công khách hàng cho quản lý"),
    
    // Assignment permissions
    ASSIGNMENT_VIEW("Xem phân công"),
    ASSIGNMENT_CREATE("Tạo phân công"),
    ASSIGNMENT_UPDATE("Cập nhật phân công"),
    ASSIGNMENT_REASSIGN("Điều động nhân viên"),
    ASSIGNMENT_DELETE("Xóa phân công"),
    
    // Attendance permissions
    ATTENDANCE_VIEW("Xem chấm công"),
    ATTENDANCE_CREATE("Tạo chấm công"),
    ATTENDANCE_EDIT("Chỉnh sửa chấm công"),
    ATTENDANCE_DELETE("Xóa chấm công"),
    ATTENDANCE_EXPORT("Xuất Excel chấm công"),
    
    // Payroll permissions
    PAYROLL_VIEW("Xem bảng lương"),
    PAYROLL_CREATE("Tạo bảng lương"),
    PAYROLL_EDIT("Chỉnh sửa bảng lương"),
    PAYROLL_MARK_PAID("Đánh dấu đã trả lương"),
    PAYROLL_ADVANCE("Quản lý ứng lương"),
    PAYROLL_EXPORT("Xuất Excel bảng lương"),
    
    // Cost management
    COST_MANAGE("Quản lý chi phí (bonus, penalty, allowance)"),
    
    // Contract permissions
    CONTRACT_VIEW("Xem hợp đồng"),
    CONTRACT_CREATE("Tạo hợp đồng"),
    CONTRACT_EDIT("Chỉnh sửa hợp đồng"),
    CONTRACT_DELETE("Xóa hợp đồng"),
    
    // User management
    USER_VIEW("Xem thông tin user"),
    USER_CREATE("Tạo user mới"),
    USER_EDIT("Chỉnh sửa thông tin user"),
    USER_DELETE("Xóa user"),
    USER_MANAGE_ALL("Quản lý tất cả user"),

    SERVICE_VIEW("Xem danh sách dịch vụ"),
    SERVICE_CREATE("Tạo dịch vụ mới"),
    SERVICE_EDIT("Chỉnh sửa dịch vụ"),
    SERVICE_DELETE("Xóa dịch vụ"),
    SERVICE_EXPORT("Xuất dữ liệu dịch vụ / phục vụ xuất hóa đơn"),

    // Profile change request
    REQUEST_PROFILE_CHANGE("Yêu cầu thay đổi thông tin cá nhân"),
    APPROVE_PROFILE_CHANGE("Phê duyệt thay đổi thông tin"),

    // Rating
    REVIEW_CREATE("Đánh giá nhân viên"),
    REVIEW_UPDATE("Cập nhật đánh giá nhân viên"),
    REVIEW_DELETE("Xóa đánh giá nhân viên"),
    REVIEW_VIEW_ALL("Xem danh sách đánh giá"),
    REVIEW_VIEW_CONTRACT("Xem danh sách đánh giá của hợp đồng"),

    // Audit
    AUDIT_VIEW("Xem lịch sử thay đổi");
    
    private final String description;
    
    Permission(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}

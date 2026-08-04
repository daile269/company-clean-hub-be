package com.company.company_clean_hub_be.entity;

public enum NotificationType {
    WORK_TIME_CONFLICT("Cảnh báo trùng khung giờ làm việc"),
    NEW_EMPLOYEE_CREATED("Nhân viên mới được thêm vào hệ thống"),
    MISSING_VERIFICATION_PHOTO("Nhân viên quên chụp hình xác minh"),
    INSUFFICIENT_STAFF("Thiếu nhân viên phụ trách hợp đồng"),
    CONTRACT_EXPIRING("Hợp đồng sắp hết hạn"),
    ASSIGNMENT_OVER_BUDGET("Phân công vượt ngân sách"),
    TEMPORARY_OVER_5_DAYS("Điều động tạm thời quá 5 ngày"),
    CHECKIN_OUTSIDE_RADIUS("Check-in ngoài bán kính cho phép");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

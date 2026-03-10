package com.company.company_clean_hub_be.entity;

public enum NotificationType {
    WORK_TIME_CONFLICT("Cảnh báo trùng khung giờ làm việc"),
    NEW_EMPLOYEE_CREATED("Nhân viên mới được thêm vào hệ thống");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

package com.company.company_clean_hub_be.entity;

public enum WorkScheduleStatus {
    SCHEDULED("Chờ chụp ảnh"),
    VERIFIED("Đã chụp ảnh/đã duyệt"),
    MISSED("Không chụp ảnh"),
    CANCELLED("Đã hủy");

    private final String description;

    WorkScheduleStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

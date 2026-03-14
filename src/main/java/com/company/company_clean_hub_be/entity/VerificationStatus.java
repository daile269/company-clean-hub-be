package com.company.company_clean_hub_be.entity;

public enum VerificationStatus {
    PENDING("Chờ xác minh"),
    IN_PROGRESS("Đang chụp ảnh"),
    APPROVED("Đã duyệt"),
    AUTO_APPROVED("Tự động duyệt sau 5 lần");

    private final String description;

    VerificationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
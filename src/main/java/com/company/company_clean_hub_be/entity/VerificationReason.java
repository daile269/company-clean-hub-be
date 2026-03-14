package com.company.company_clean_hub_be.entity;

public enum VerificationReason {
    NEW_EMPLOYEE("Nhân viên mới - lần đầu được phân công"),
    CONTRACT_SETTING("Theo cài đặt hợp đồng yêu cầu xác minh");

    private final String description;

    VerificationReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
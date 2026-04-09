package com.company.company_clean_hub_be.entity;

public enum WorkScheduleReason {
    NEW_EMPLOYEE_VERIFICATION("Xác minh nhân viên mới"),
    CONTRACT_REQUIREMENT("Yêu cầu chấm công hợp đồng");

    private final String description;

    WorkScheduleReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

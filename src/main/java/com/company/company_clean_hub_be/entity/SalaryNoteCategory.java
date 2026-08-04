package com.company.company_clean_hub_be.entity;

public enum SalaryNoteCategory {
    MONTHLY_ASSIGNMENT("Phân công tính lương tháng"),
    DAILY_ASSIGNMENT("Phân công tính lương ngày");

    private final String description;

    SalaryNoteCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

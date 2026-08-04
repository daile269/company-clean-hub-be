package com.company.company_clean_hub_be.entity;

public enum SalaryNoteType {
    FIXED("Cố định"),
    TEMPORARY("Tạm thời");

    private final String description;

    SalaryNoteType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

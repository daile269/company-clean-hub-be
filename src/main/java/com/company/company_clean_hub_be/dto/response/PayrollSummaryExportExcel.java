package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSummaryExportExcel {
    // Employee basic info
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private String bankName;
    private String bankAccount;
    private String phone;
    
    // Projects list
    private List<String> projects; // Danh sách các công ty/dự án
    
    // Summary totals
    private Integer totalDays;
    private Integer totalPlannedDays;
    private BigDecimal totalBonus;
    private BigDecimal totalPenalty;
    private BigDecimal totalAllowance;
    private BigDecimal insuranceAmount;
    private BigDecimal advanceSalary;
    private BigDecimal finalSalary;
}

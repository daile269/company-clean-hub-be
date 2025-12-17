package com.company.company_clean_hub_be.dto.response;
import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayRollAssignmentExportExcel {
    // Employee info (will be merged across assignments)
    private Long employeeId;
    private String employeeName;
    private String bankName;
    private String bankAccount;
    private String phone;
    
    // Assignment-specific info
    private String assignmentType; // Mức lương (mapped to Vietnamese)
    private String projectCompany; // Công trình
    private BigDecimal baseSalary;
    // Assignment-level totals
    private Integer assignmentDays;
    private Integer assignmentPlanedDays;
    private BigDecimal assignmentBonus;
    private BigDecimal assignmentPenalty;
    private BigDecimal assignmentAllowance;
    private BigDecimal companyAllowance;
    private BigDecimal assignmentInsurance;
    private BigDecimal assignmentAdvance;
    private BigDecimal assignmentSalary;
    
    // Employee-level totals (for the total row)
    private Integer totalDays;
    private Integer totalPlanedDays;
    private BigDecimal totalBonus;
    private BigDecimal totalPenalty;
    private BigDecimal totalAllowance;
    private BigDecimal totalInsurance;
    private BigDecimal totalSalaryBeforeAdvance; // Salary before deducting advances
    private BigDecimal totalAdvance;
    private BigDecimal paidAmount; // Số tiền đã thanh toán sớm
    private BigDecimal finalSalary;
    
    // Flag to indicate if this is a total row
    private Boolean isTotalRow;
    
    // Note containing calculation formulas
    private String note;
}


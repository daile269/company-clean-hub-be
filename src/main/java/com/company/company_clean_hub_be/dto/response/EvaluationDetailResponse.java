package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.company.company_clean_hub_be.entity.AssignmentScope;
import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.AssignmentType;
import com.company.company_clean_hub_be.entity.EvaluationStatus;
import com.company.company_clean_hub_be.entity.EmploymentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDetailResponse {
    
    // Evaluation info
    private Long evaluationId;
    private EvaluationStatus evaluationStatus;
    private String internalNotes;
    private LocalDateTime evaluatedAt;
    private String evaluatedByUsername;
    private String evaluatedByName;
    
    // Attendance info
    private Long attendanceId;
    private LocalDate attendanceDate;
    private BigDecimal bonus;
    private BigDecimal penalty;
    private BigDecimal supportCost;
    private BigDecimal workHours;
    private Boolean isOvertime;
    private BigDecimal overtimeAmount;
    private String attendanceDescription;
    private String approvedByUsername;
    private String approvedByName;
    
    // Employee info
    private Long employeeId;
    private String employeeName;
    private String employeePhone;
    private String employeeEmail;
    private EmploymentType employmentType;
    private BigDecimal baseSalary;
    
    // Assignment info
    private Long assignmentId;
    private LocalDate assignmentStartDate;
    private LocalDate assignmentEndDate;
    private AssignmentStatus assignmentStatus;
    private BigDecimal salaryAtTime;
    private Integer workDays;
    private Integer plannedDays;
    private List<String> workingDaysPerWeek;
    private BigDecimal additionalAllowance;
    private String assignmentDescription;
    private AssignmentType assignmentType;
    private AssignmentScope assignmentScope;
    private String assignedByUsername;
    private String assignedByName;
    
    // Contract info (if applicable)
    private Long contractId;
    private String contractTitle;
    private String contractDescription;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private BigDecimal contractValue;
    
    // Customer info (if applicable)
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    
    // Service info (if applicable)
    private Long serviceId;
    private String serviceName;
    private String serviceDescription;
    private BigDecimal servicePrice;
    
    // Verification info (if applicable)
    private Long verificationId;
    private String verificationStatus;
    private String verificationNotes;
    private List<VerificationImageInfo> verificationImages;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationImageInfo {
        private Long imageId;
        private String imageUrl;
        private String imageType;
        private String uploadedAt;
    }
}
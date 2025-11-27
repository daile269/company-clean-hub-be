package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceResponse {

    private Long id;
    
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    
    private Long assignmentId;
    private String assignmentType;
    private Long customerId;
    private String customerName;
    
    private LocalDate date;
    private BigDecimal workHours;
    private BigDecimal bonus;
    private BigDecimal penalty;
    private BigDecimal supportCost;
    private Boolean isOvertime;
    private BigDecimal overtimeAmount;
    
    private Long approvedBy;
    private String approvedByName;
    
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

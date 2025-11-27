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
public class AssignmentResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Long customerId;
    private String customerName;
    private String customerCode;
    private LocalDate startDate;
    private String status;
    private BigDecimal salaryAtTime;
    private Integer workDays;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String assignmentType;
}

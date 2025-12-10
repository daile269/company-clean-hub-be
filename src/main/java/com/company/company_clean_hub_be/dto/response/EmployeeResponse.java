package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponse {
    private Long id;
    private String employeeCode;
    private String username;
    private String phone;
    private String email;
    private Long roleId;
    private String roleName;
    private String status;
    private String cccd;
    private String address;
    private String name;
    private String bankAccount;
    private String bankName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private EmploymentType employmentType;

    // COMPANY_STAFF fields
    private BigDecimal monthlySalary;
    private BigDecimal allowance;
    private BigDecimal insuranceSalary;

}

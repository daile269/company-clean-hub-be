package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeExportDto {
    private Long id;
    private String employeeCode;
    private String name;
    private String username;
    private String email;
    private String phone;
    private String address;
    private String cccd;
    private String bankAccount;
    private String bankName;
    private String description;
    private String createdAt;
    private String updatedAt;
}

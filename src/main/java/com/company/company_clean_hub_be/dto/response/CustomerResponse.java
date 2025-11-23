package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
    private Long id;
    private String customerCode;
    private String username;
    private String phone;
    private String email;
    private Long roleId;
    private String roleName;
    private String status;
    private String name;
    private String address;
    private String contactInfo;
    private String taxCode;
    private String description;
    private String company;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

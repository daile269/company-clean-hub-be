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
public class CustomerAssignmentResponse {

    private Long id;
    private Long managerId;
    private String managerName;
    private String managerUsername;
    private Long customerId;
    private String customerName;
    private String customerCode;
    private Long assignedById;
    private String assignedByName;
    private LocalDateTime createdAt;
}

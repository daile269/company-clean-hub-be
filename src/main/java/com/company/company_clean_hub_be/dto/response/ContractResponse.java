package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private List<ServiceResponse> services;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<DayOfWeek> workingDaysPerWeek;
    private ContractType contractType;
    private String paymentStatus;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

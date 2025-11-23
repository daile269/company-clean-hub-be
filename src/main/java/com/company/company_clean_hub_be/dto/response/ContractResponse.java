package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractResponse {

    private Long id;
    private Long customerId;
    private String customerName;
    private Set<Long> serviceIds;
    private Set<String> serviceNames;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal basePrice;
    private BigDecimal vat;
    private BigDecimal total;
    private BigDecimal extraCost;
    private BigDecimal discountCost;
    private BigDecimal finalPrice;
    private String paymentStatus;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

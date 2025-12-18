package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ServiceType;
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
public class ServiceResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal vat;
    private LocalDate effectiveFrom;
    private ServiceType serviceType;
    private BigDecimal amount;
    private BigDecimal baseAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

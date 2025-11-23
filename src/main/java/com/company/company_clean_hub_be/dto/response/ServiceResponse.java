package com.company.company_clean_hub_be.dto.response;

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
public class ServiceResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal priceFrom;
    private BigDecimal priceTo;
    private String mainImage;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

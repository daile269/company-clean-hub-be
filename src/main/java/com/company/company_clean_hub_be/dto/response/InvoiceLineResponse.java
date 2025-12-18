package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ServiceType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceLineResponse {
    Long id;
    Long serviceId;
    String title;
    String description;
    ServiceType serviceType;
    String unit;
    Integer quantity;
    BigDecimal price;
    BigDecimal baseAmount;
    BigDecimal vat;
    BigDecimal vatAmount;
    BigDecimal totalAmount;
    Integer contractDays;
    Integer actualDays;
    LocalDate effectiveFrom;
    LocalDateTime createdAt;
    String createdAtFull;
}

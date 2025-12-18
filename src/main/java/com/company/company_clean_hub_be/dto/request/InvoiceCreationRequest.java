package com.company.company_clean_hub_be.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceCreationRequest {
    Long contractId;
    Integer invoiceMonth;  // 1-12
    Integer invoiceYear;   // Ví dụ: 2025
    String notes;

    // Cho bulk creation by customer
    Long customerId;
}

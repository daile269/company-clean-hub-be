package com.company.company_clean_hub_be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkInvoiceResponse {
    Integer totalContracts;
    Integer successfulInvoices;
    Integer failedInvoices;
    List<InvoiceResponse> createdInvoices;
    List<String> errors;
}

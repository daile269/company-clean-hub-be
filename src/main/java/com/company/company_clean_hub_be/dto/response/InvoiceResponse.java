package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ContractType;
import com.company.company_clean_hub_be.entity.InvoiceStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.company.company_clean_hub_be.dto.response.InvoiceLineResponse;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceResponse {
    Long id;
    Long contractId;
    Long customerId;
    String customerName;
    String customerPhone;
    String customerAddress;
    String customerTaxCode;
    Integer invoiceMonth;
    Integer invoiceYear;
    Integer actualWorkingDays;
    BigDecimal subtotal;
    BigDecimal vatPercentage;
    BigDecimal vatAmount;
    BigDecimal totalAmount;
    ContractType invoiceType;
    List<InvoiceLineResponse> invoiceLines;
    String notes;
    InvoiceStatus status;
    LocalDateTime createdAt;
    String createdAtFull;
    LocalDateTime paidAt;
    String createdByUsername;
}

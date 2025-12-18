package com.company.company_clean_hub_be.dto.request;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollUpdateRequest {
    // Optional adjustments provided by user. If null, system will use computed values.
    private BigDecimal allowanceTotal;
    private BigDecimal insuranceTotal;
    private BigDecimal advanceTotal;
    private BigDecimal paidAmount;  // Số tiền đã thanh toán sớm
}

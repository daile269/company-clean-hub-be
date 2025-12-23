package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryResponse {
    private Long id;
    private Long payrollId;
    private LocalDateTime paymentDate;
    private BigDecimal amount;
    private Integer installmentNumber;
    private LocalDateTime createdAt;
}

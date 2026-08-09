package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @Positive(message = "Số tiền thanh toán phải lớn hơn 0")
    private BigDecimal amount;
}

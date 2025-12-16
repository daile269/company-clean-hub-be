package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRequest {

    // Optional - if null, calculate for all employees
    private Long employeeId;

    @NotNull(message = "Tháng không được để trống")
    private Integer month;

    @NotNull(message = "Năm không được để trống")
    private Integer year;

    // Bảo hiểm (có thể null)
    private java.math.BigDecimal insuranceAmount;

    // Tiền ứng lương (có thể null)
    private java.math.BigDecimal advanceSalary;
}

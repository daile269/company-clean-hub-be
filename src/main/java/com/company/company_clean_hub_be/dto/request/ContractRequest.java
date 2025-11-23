package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractRequest {

    @NotNull(message = "ID khách hàng không được để trống")
    private Long customerId;

    @NotNull(message = "Danh sách dịch vụ không được để trống")
    @Size(min = 1, message = "Phải có ít nhất một dịch vụ")
    private Set<Long> serviceIds;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    private LocalDate endDate;

    @PositiveOrZero(message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    @PositiveOrZero(message = "VAT phải lớn hơn hoặc bằng 0")
    private BigDecimal vat;

    @PositiveOrZero(message = "Tổng tiền phải lớn hơn hoặc bằng 0")
    private BigDecimal total;

    @PositiveOrZero(message = "Chi phí phát sinh phải lớn hơn hoặc bằng 0")
    private BigDecimal extraCost;

    @PositiveOrZero(message = "Chi phí giảm giá phải lớn hơn hoặc bằng 0")
    private BigDecimal discountCost;

    @PositiveOrZero(message = "Giá cuối cùng phải lớn hơn hoặc bằng 0")
    private BigDecimal finalPrice;

    @Size(max = 50, message = "Trạng thái thanh toán không được vượt quá 50 ký tự")
    private String paymentStatus;

    private String description;
}

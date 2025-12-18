package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.ServiceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateServiceInContractRequest {

    @NotNull(message = "ID dịch vụ không được để trống")
    private Long serviceId;

    @Size(max = 200, message = "Tiêu đề không được vượt quá 200 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @PositiveOrZero(message = "Giá phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    @PositiveOrZero(message = "VAT phải lớn hơn hoặc bằng 0")
    private BigDecimal vat;

    private LocalDate effectiveFrom;

    private ServiceType serviceType;
}

package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.SalaryNoteCategory;
import com.company.company_clean_hub_be.entity.SalaryNoteType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryNoteRequest {
    private Long contractId;

    @NotNull(message = "Loại phân công không được để trống")
    private SalaryNoteCategory category;

    @NotNull(message = "Loại lương không được để trống")
    private SalaryNoteType salaryType;

    @PositiveOrZero(message = "Số tiền phải lớn hơn hoặc bằng 0")
    private BigDecimal amount;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;
}

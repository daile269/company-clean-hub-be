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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentRequest {

    @NotNull(message = "ID nhân viên không được để trống")
    private Long employeeId;

    @NotNull(message = "ID hợp đồng không được để trống")
    private Long contractId;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;

    @Size(max = 50, message = "Trạng thái không được vượt quá 50 ký tự")
    private String status;

    @PositiveOrZero(message = "Lương tại thời điểm phải lớn hơn hoặc bằng 0")
    private BigDecimal salaryAtTime;

    @PositiveOrZero(message = "Số ngày làm việc phải lớn hơn hoặc bằng 0")
    private Integer workDays;

    private String description;
}

package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class AttendanceRequest {

    @NotNull(message = "ID nhân viên không được để trống")
    private Long employeeId;

    @NotNull(message = "ID phân công không được để trống")
    private Long assignmentId;

    @NotNull(message = "Ngày chấm công không được để trống")
    private LocalDate date;

    @PositiveOrZero(message = "Số giờ làm việc phải lớn hơn hoặc bằng 0")
    private BigDecimal workHours;

    @PositiveOrZero(message = "Tiền thưởng phải lớn hơn hoặc bằng 0")
    private BigDecimal bonus;

    @PositiveOrZero(message = "Tiền phạt phải lớn hơn hoặc bằng 0")
    private BigDecimal penalty;

    @PositiveOrZero(message = "Chi phí hỗ trợ phải lớn hơn hoặc bằng 0")
    private BigDecimal supportCost;

    private Boolean isOvertime;

    @PositiveOrZero(message = "Tiền làm thêm giờ phải lớn hơn hoặc bằng 0")
    private BigDecimal overtimeAmount;

    private Long approvedBy;

    private String description;
}

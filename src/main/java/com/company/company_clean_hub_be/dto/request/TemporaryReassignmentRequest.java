package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryReassignmentRequest {

    @NotNull(message = "ID nhân viên đi làm thay không được để trống")
    private Long replacementEmployeeId;

    @NotNull(message = "ID nhân viên bị thay không được để trống")
    private Long replacedEmployeeId;

    // ID phân công của nhân viên bị thay (để xác định chính xác attendance cần điều động)
    private Long replacedAssignmentId;

    @NotEmpty(message = "Danh sách ngày điều động không được để trống")
    private List<LocalDate> dates;

    @PositiveOrZero(message = "Lương theo ngày phải là số lớn hơn hoặc bằng 0")
    private BigDecimal salaryAtTime;

    private String description;
}

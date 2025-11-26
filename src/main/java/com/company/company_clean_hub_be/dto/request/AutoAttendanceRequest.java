package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoAttendanceRequest {

    @NotNull(message = "ID nhân viên không được để trống")
    private Long employeeId;

    @NotNull(message = "ID phân công không được để trống")
    private Long assignmentId;

    @NotNull(message = "Tháng không được để trống")
    private Integer month;

    @NotNull(message = "Năm không được để trống")
    private Integer year;

    // Danh sách ngày nghỉ (nếu có)
    private List<LocalDate> excludeDates;
}

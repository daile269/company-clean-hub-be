package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemporaryAssignmentRequest {

    @NotNull(message = "ID nhân viên đi làm thay không được để trống")
    private Long replacementEmployeeId;

    @NotNull(message = "ID phân công của nhân viên đi làm thay không được để trống")
    private Long replacementAssignmentId;

    @NotNull(message = "ID nhân viên bị thay không được để trống")
    private Long replacedEmployeeId;

    @NotNull(message = "Ngày điều động không được để trống")
    private LocalDate date;

    private String description;
}

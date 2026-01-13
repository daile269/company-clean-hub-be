package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.DayOfWeek;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class AssignmentRequest {
    @NotNull(message = "ID nhân viên không được để trống")
    private Long employeeId;
    // Optional for COMPANY scope, required for CONTRACT scope
    private Long contractId;
    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDate startDate;
    @Builder.Default
    private com.company.company_clean_hub_be.entity.AssignmentScope scope = com.company.company_clean_hub_be.entity.AssignmentScope.CONTRACT;
    private AssignmentStatus status;
    private String assignmentType;
    // For SUPPORT assignments: explicit dates to create attendances for
    private List<java.time.LocalDate> dates;
    @PositiveOrZero(message = "Lương tại thời điểm phải lớn hơn hoặc bằng 0")
    private BigDecimal salaryAtTime;
    @PositiveOrZero(message = "Phụ cấp thêm phải lớn hơn hoặc bằng 0")
    private BigDecimal additionalAllowance;
    // Required for COMPANY scope (days per week employee works)
    private List<java.time.DayOfWeek> workingDaysPerWeek;
    private String description;
    @PositiveOrZero(message = "Planned days phải lớn hơn hoặc bằng 0")
    private Integer plannedDays;
}

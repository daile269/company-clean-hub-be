package com.company.company_clean_hub_be.dto.response;


import com.company.company_clean_hub_be.entity.AssignmentStatus;
import com.company.company_clean_hub_be.entity.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private Long customerId;
    private String customerName;
    private String customerCode;
    private Long contractId;
    private String contractDescription;
    private java.time.LocalDate contractStartDate;
    private java.time.LocalDate contractEndDate;
    private ContractType contractType;
    private LocalDate startDate;
    private AssignmentStatus status;
    private BigDecimal salaryAtTime;
    private Integer workDays;
    private Integer plannedDays;
    private List<DayOfWeek> workingDaysPerWeek;
    private BigDecimal additionalAllowance;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String assignmentType;
    private String scope;  // CONTRACT or COMPANY
}

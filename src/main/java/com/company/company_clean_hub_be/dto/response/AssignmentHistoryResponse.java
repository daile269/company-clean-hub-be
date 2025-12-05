package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.HistoryStatus;
import com.company.company_clean_hub_be.entity.ReassignmentType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignmentHistoryResponse {
    Long id;
    Long oldAssignmentId;
    Long newAssignmentId;
    Long replacedEmployeeId;
    String replacedEmployeeName;
    Long replacementEmployeeId;
    String replacementEmployeeName;
    Long contractId;
    String customerName;
    List<LocalDate> reassignmentDates;
    ReassignmentType reassignmentType;
    String notes;
    HistoryStatus status;
    String createdByUsername;
    LocalDateTime createdAt;
    String rollbackByUsername;
    LocalDateTime rollbackAt;
}

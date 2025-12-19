package com.company.company_clean_hub_be.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDeleteRequest {
    @NotNull
    private LocalDate date;

    @NotNull
    private Long contractId;

    @NotNull
    private Long employeeId;

    private String description;
}

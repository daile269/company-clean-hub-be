package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleContractSummary {
    private Long contractId;
    private String contractCode;
    private String customerName;
    private Long customerId;
    private List<String> serviceNames;
    private Integer totalEmployees;
    private Long totalSchedules;
    private Long verifiedCount;
    private Long missedCount;
    private Long scheduledCount;
    private Double verifiedPercentage;
}

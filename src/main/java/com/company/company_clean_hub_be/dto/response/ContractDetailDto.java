package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractDetailDto {
    private Long contractId;
    private String contractCode;
    private String startDate;
    private String endDate;
    private String workingDays;
    private Double contractValue;
    private Integer workDays;
    private Double vatAmount;
    private Double totalValue;
    private String contractType;
    private String paymentStatus;
    private String description;
}
